package com.openkm.module.db.stuff;

import com.openkm.extractor.RegisteredExtractors;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.util.BytesRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class LazyField extends Field {

    private static final Logger log = LoggerFactory.getLogger(LazyField.class);

    private final PersistentFile persistentFile;
    private String cachedContent;

    public LazyField(String name, PersistentFile persistentFile, FieldType fieldType) {
        // give an empty string initially; value will be resolved lazily
        super(name, new BytesRef(), fieldType);
        this.persistentFile = persistentFile;
    }

    @Override
    public String stringValue() {
        if (cachedContent == null) {
            try {
                cachedContent = RegisteredExtractors.getText(persistentFile);
                // update the underlying value so Lucene can use it for indexing
                this.fieldsData = new BytesRef(cachedContent);
                log.debug("Loaded lazy content for {}", persistentFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract text from " + persistentFile, e);
            }
        }
        return cachedContent;
    }
}
