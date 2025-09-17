package com.openkm.module.db.stuff;

import com.openkm.extractor.RegisteredExtractors;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LazyFieldBridge implements PropertyBridge<PersistentFile> {

    private static final Logger log = LoggerFactory.getLogger(LazyFieldBridge.class);
    private final IndexFieldReference<String> contentField;

    public LazyFieldBridge(IndexFieldReference<String> contentField) {
        this.contentField = contentField;
    }

    @Override
    public void write(DocumentElement target,
                      PersistentFile persistentFile,
                      PropertyBridgeWriteContext context) {
        if (persistentFile == null) {
            return;
        }
        try {
            // Extract only when indexing
            String text = RegisteredExtractors.getText(persistentFile);
            target.addValue(contentField, text);
            log.debug("Indexed lazy content for {}", persistentFile);
        } catch (IOException e) {
            log.error("Text extraction failed for {}", persistentFile, e);
        }
    }

    /**
     * Binder to register the bridge in your entity mapping.
     */
    public static class Binder implements PropertyBinder {
        @Override
        public void bind(PropertyBindingContext ctx) {
            IndexFieldReference<String> ref = ctx.indexSchemaElement()
                    .field(ctx.bridgedElement().name(),
                           f -> f.asString().analyzer("standard"))
                    .toReference();
            ctx.bridge(PersistentFile.class, new LazyFieldBridge(ref));
        }
    }
}
