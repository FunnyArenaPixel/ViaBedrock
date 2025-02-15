/*
 * This file is part of ViaBedrock - https://github.com/RaphiMC/ViaBedrock
 * Copyright (C) 2023 RK_01/RaphiMC and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.raphimc.viabedrock.protocol.model;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.util.GsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.raphimc.viabedrock.ViaBedrock;
import net.raphimc.viabedrock.api.util.MathUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ResourcePack {

    private static final byte[] CONTENTS_JSON_ENCRYPTED_MAGIC = new byte[]{(byte) 0xFC, (byte) 0xB9, (byte) 0xCF, (byte) 0x9B};

    private final UUID packId;
    private String version;
    private final String contentKey;
    private final String subPackName;
    private final String contentId;
    private final boolean scripting;
    private final boolean raytracingCapable;

    private byte[] hash;
    private boolean premium;
    private int type;

    private byte[] compressedData;
    private int maxChunkSize;
    private boolean[] receivedChunks;
    private Content content;

    public ResourcePack(final UUID packId, final String version, final String contentKey, final String subPackName, final String contentId, final boolean scripting, final boolean raytracingCapable, final long compressedSize, final int type) {
        this.packId = packId;
        this.version = version;
        this.contentKey = contentKey;
        this.subPackName = subPackName;
        this.contentId = contentId;
        this.scripting = scripting;
        this.raytracingCapable = raytracingCapable;
        this.compressedData = new byte[(int) compressedSize];
        this.type = type;
    }

    public boolean processDataChunk(final int chunkIndex, final byte[] data) throws NoSuchAlgorithmException, IOException, InvalidAlgorithmParameterException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (this.receivedChunks[chunkIndex]) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received duplicate resource pack chunk data: " + this.packId);
            return false;
        }

        final int offset = chunkIndex * this.maxChunkSize;
        if (offset + data.length > this.compressedData.length) {
            ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Received resource pack chunk data with invalid offset: " + this.packId);
            return false;
        }
        System.arraycopy(data, 0, this.compressedData, offset, data.length);
        this.receivedChunks[chunkIndex] = true;

        if (this.hasReceivedAllChunks()) {
            this.decompressAndDecrypt();
            return true;
        }

        return false;
    }

    public boolean isDecompressed() {
        return this.compressedData == null;
    }

    public UUID packId() {
        return this.packId;
    }

    public String version() {
        return this.version;
    }

    public void setVersion(final String version) {
        this.version = version;
    }

    public String contentKey() {
        return this.contentKey;
    }

    public String subPackName() {
        return this.subPackName;
    }

    public String contentId() {
        return this.contentId;
    }

    public boolean scripting() {
        return this.scripting;
    }

    public boolean raytracingCapable() {
        return this.raytracingCapable;
    }

    public void setHash(final byte[] hash) {
        this.hash = hash;
    }

    public boolean premium() {
        return this.premium;
    }

    public void setPremium(final boolean premium) {
        this.premium = premium;
    }

    public int type() {
        return this.type;
    }

    public void setType(final int type) {
        this.type = type;
    }

    public int compressedDataLength() {
        if (this.compressedData == null) {
            return 0;
        }

        return this.compressedData.length;
    }

    public void setCompressedDataLength(final int length, final int maxChunkSize) {
        this.compressedData = new byte[length];
        this.maxChunkSize = maxChunkSize;
        this.receivedChunks = new boolean[MathUtil.ceil((float) this.compressedData.length / maxChunkSize)];
    }

    public Content content() {
        if (!this.isDecompressed()) {
            throw new IllegalStateException("Pack is not decompressed");
        }

        return this.content;
    }

    private void decompressAndDecrypt() throws NoSuchAlgorithmException, IOException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        if (this.hash != null) {
            final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            final byte[] hash = sha256.digest(this.compressedData);
            if (!Arrays.equals(hash, this.hash)) {
                throw new IllegalStateException("Resource pack hash mismatch: " + this.packId);
            }
        }

        this.content = new Content();
        final ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(this.compressedData));
        ZipEntry zipEntry;
        int len;
        final byte[] buf = new byte[4096];
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((zipEntry = zipInputStream.getNextEntry()) != null) {
            while ((len = zipInputStream.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            this.content.put(zipEntry.getName(), baos.toByteArray());
            baos.reset();
        }
        this.compressedData = null;

        if (!this.contentKey.isEmpty()) {
            final Cipher aesCfb8 = Cipher.getInstance("AES/CFB8/NoPadding");
            final byte[] contentKeyBytes = this.contentKey.getBytes(StandardCharsets.ISO_8859_1);
            aesCfb8.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKeyBytes, "AES"), new IvParameterSpec(Arrays.copyOfRange(contentKeyBytes, 0, 16)));
            final ByteBuf contents = Unpooled.wrappedBuffer(this.content.get("contents.json"));
            contents.skipBytes(4); // version
            final byte[] magic = new byte[4];
            contents.readBytes(magic); // magic
            if (!Arrays.equals(magic, CONTENTS_JSON_ENCRYPTED_MAGIC)) {
                throw new IllegalStateException("contents.json magic mismatch: " + Arrays.toString(CONTENTS_JSON_ENCRYPTED_MAGIC) + " != " + Arrays.toString(magic));
            }
            contents.readerIndex(16);
            final short contentIdLength = contents.readUnsignedByte(); // content id length
            final byte[] contentIdBytes = new byte[contentIdLength];
            contents.readBytes(contentIdBytes); // content id
            final String contentId = new String(contentIdBytes, StandardCharsets.UTF_8);
            if (!this.contentId.equalsIgnoreCase(contentId)) {
                throw new IllegalStateException("contents.json contentId mismatch: " + this.contentId + " != " + contentId);
            }
            contents.readerIndex(256);
            final byte[] encryptedContents = new byte[contents.readableBytes()];
            contents.readBytes(encryptedContents); // encrypted contents.json
            this.content.put("contents.json", aesCfb8.doFinal(encryptedContents));

            final JsonObject contentsJson = this.content.getJson("contents.json");
            final JsonArray contentArray = contentsJson.getAsJsonArray("content");
            for (JsonElement element : contentArray) {
                final JsonObject contentItem = element.getAsJsonObject();
                if (!contentItem.has("key")) continue;
                final String key = contentItem.get("key").getAsString();
                final String path = contentItem.get("path").getAsString();
                if (!this.content.containsKey(path)) {
                    ViaBedrock.getPlatform().getLogger().log(Level.WARNING, "Missing resource pack file: " + path);
                    continue;
                }
                final byte[] encryptedData = this.content.get(path);
                final byte[] keyBytes = key.getBytes(StandardCharsets.ISO_8859_1);
                aesCfb8.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(Arrays.copyOfRange(keyBytes, 0, 16)));
                this.content.put(path, aesCfb8.doFinal(encryptedData));
            }
        }
    }

    private boolean hasReceivedAllChunks() {
        for (final boolean receivedChunk : this.receivedChunks) {
            if (!receivedChunk) {
                return false;
            }
        }
        return true;
    }

    public static class Content extends HashMap<String, byte[]> {

        public String getString(final String path) {
            final byte[] bytes = this.get(path);
            if (bytes == null) {
                return null;
            }

            return new String(bytes, StandardCharsets.UTF_8);
        }

        public boolean putString(final String path, final String string) {
            return this.put(path, string.getBytes(StandardCharsets.UTF_8)) != null;
        }

        public List<String> getLines(final String path) {
            final String string = this.getString(path);
            if (string == null) {
                return null;
            }

            return Arrays.asList(string.split("\\n"));
        }

        public boolean putLines(final String path, final List<String> lines) {
            return this.putString(path, String.join("\\n", lines));
        }

        public JsonObject getJson(final String path) {
            final String string = this.getString(path);
            if (string == null) {
                return null;
            }

            return GsonUtil.getGson().fromJson(string.trim(), JsonObject.class);
        }

        public boolean putJson(final String path, final JsonObject json) {
            return this.putString(path, GsonUtil.getGson().toJson(json));
        }

        public BufferedImage getImage(final String path) {
            final byte[] bytes = this.get(path);
            if (bytes == null) {
                return null;
            }

            try {
                return ImageIO.read(new ByteArrayInputStream(bytes));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean putImage(final String path, final BufferedImage image) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                ImageIO.write(image, "png", baos);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return this.put(path, baos.toByteArray()) != null;
        }

        public byte[] toZip() throws IOException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream(4 * 1024 * 1024);
            final ZipOutputStream zipOutputStream = new ZipOutputStream(baos);
            for (final Map.Entry<String, byte[]> entry : this.entrySet()) {
                zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutputStream.write(entry.getValue());
                zipOutputStream.closeEntry();
            }
            zipOutputStream.close();
            return baos.toByteArray();
        }

    }

}
