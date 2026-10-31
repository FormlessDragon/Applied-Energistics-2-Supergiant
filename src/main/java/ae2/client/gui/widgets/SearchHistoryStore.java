/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package ae2.client.gui.widgets;

import ae2.core.AELog;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Remembers the terms that were searched for in any AE2 search field.
 * <p>
 * Entries are kept de-duplicated case-insensitively, the most recent term comes first and at most
 * {@value #MAX_ENTRIES} terms are retained. The list is stored as a plain text file with one term per line.
 */
@SideOnly(Side.CLIENT)
public final class SearchHistoryStore {
    public static final int MAX_ENTRIES = 5;

    private static final int MAX_LENGTH = 128;
    private static final String DATA_DIR = "ae2";
    private static final String DATA_FILE = "search_history.txt";

    private static final List<String> ENTRIES = new ArrayList<>();
    private static boolean loaded;

    private SearchHistoryStore() {
    }

    /**
     * @return The stored terms, most recent first. The returned list is an immutable snapshot.
     */
    public static synchronized List<String> getEntries() {
        load();
        return List.copyOf(ENTRIES);
    }

    /**
     * @return All stored terms that contain the given search text, most recent first.
     */
    public static List<String> filter(String searchText) {
        String needle = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return getEntries();
        }

        List<String> matches = new ArrayList<>(MAX_ENTRIES);
        for (String entry : getEntries()) {
            if (entry.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    /**
     * Moves the given term to the front of the history, dropping the oldest term if the list is full.
     */
    public static synchronized void record(String searchText) {
        String value = searchText == null ? "" : searchText.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) {
            return;
        }

        load();
        ENTRIES.removeIf(entry -> entry.equalsIgnoreCase(value));
        ENTRIES.add(0, value);
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(ENTRIES.size() - 1);
        }
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        File file = getFile();
        if (!file.isFile()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while (ENTRIES.size() < MAX_ENTRIES && (line = reader.readLine()) != null) {
                String value = line.trim();
                if (value.isEmpty() || value.length() > MAX_LENGTH) {
                    continue;
                }
                if (ENTRIES.stream().noneMatch(entry -> entry.equalsIgnoreCase(value))) {
                    ENTRIES.add(value);
                }
            }
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to load AE2 search history from %s: %s", file, e);
        }
    }

    private static void save() {
        File file = getFile();
        File directory = file.getParentFile();
        if (directory != null && !directory.isDirectory() && !directory.mkdirs()) {
            AELog.warn("Failed to create AE2 client data directory %s", directory);
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (String entry : ENTRIES) {
                writer.write(entry);
                writer.newLine();
            }
        } catch (IOException | RuntimeException e) {
            AELog.warn("Failed to save AE2 search history to %s: %s", file, e);
        }
    }

    private static File getFile() {
        return new File(new File(Minecraft.getMinecraft().gameDir, DATA_DIR), DATA_FILE);
    }
}
