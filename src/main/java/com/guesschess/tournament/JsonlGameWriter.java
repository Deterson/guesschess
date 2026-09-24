package com.guesschess.tournament;

import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Une ligne JSON par partie (etape 22 de la roadmap) - synchronized : plusieurs threads de
 * jeu (voir Tournament --threads) peuvent ecrire concurremment, un BufferedWriter ne l'est
 * pas nativement. flush() a chaque ligne : un tournoi peut tourner longtemps, mieux vaut ne
 * pas perdre de parties deja jouees si le process est interrompu.
 */
public final class JsonlGameWriter implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BufferedWriter writer;

    public JsonlGameWriter(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public synchronized void write(GameRecord record) {
        String line = MAPPER.writeValueAsString(record);
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
