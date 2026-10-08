package br.com.condominioauditoria.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Where the original files live. Never in the database. In the MVP it is a local folder; in the cloud, an object
 * store, switched by configuration without changing the callers of this interface.
 */
public interface Storage {

    /** Stores the content at the relative path (e.g. "{condominium}/BALANCETE/2026/abc-flow.pdf"). Never overwrites. */
    void store(String relativePath, InputStream content) throws IOException;

    InputStream open(String relativePath) throws IOException;

    boolean exists(String relativePath);
}
