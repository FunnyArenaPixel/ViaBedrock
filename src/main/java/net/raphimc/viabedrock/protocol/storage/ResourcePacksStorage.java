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
package net.raphimc.viabedrock.protocol.storage;

import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.viabedrock.protocol.model.ResourcePack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class ResourcePacksStorage extends StoredObject {

    private final UUID httpToken = UUID.randomUUID();
    private final Map<UUID, ResourcePack> packs = new HashMap<>();
    private boolean completed;
    private Consumer<byte[]> httpConsumer;

    public ResourcePacksStorage(final UserConnection user) {
        super(user);
    }

    public boolean hasPack(final UUID packId) {
        return this.packs.containsKey(packId);
    }

    public ResourcePack getPack(final UUID packId) {
        return this.packs.get(packId);
    }

    public Collection<ResourcePack> getPacks() {
        return this.packs.values();
    }

    public void addPack(final ResourcePack pack) {
        this.packs.put(pack.packId(), pack);
    }

    public boolean areAllPacksDecompressed() {
        return this.packs.values().stream().allMatch(ResourcePack::isDecompressed);
    }

    public UUID getHttpToken() {
        return this.httpToken;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public void setCompleted(final boolean completed) {
        this.completed = completed;
    }

    public Consumer<byte[]> getHttpConsumer() {
        return this.httpConsumer;
    }

    public void setHttpConsumer(final Consumer<byte[]> httpConsumer) {
        this.httpConsumer = httpConsumer;
    }

}
