package com.openkm.module.db.stuff;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("rawtypes")
public class LowerCaseFieldBridge implements PropertyBridge {

    private static final Logger log = LoggerFactory.getLogger(LowerCaseFieldBridge.class);
    private final IndexFieldReference<String> fieldReference;

    public LowerCaseFieldBridge(IndexFieldReference<String> fieldReference) {
        this.fieldReference = fieldReference;
    }

    @Override
    public void write(DocumentElement target, Object bridgedElement, PropertyBridgeWriteContext context) {
        if (bridgedElement instanceof String) {
            String str = ((String) bridgedElement).toLowerCase();
            log.debug("Added field with value '{}'", str);
            target.addValue(fieldReference, str);
        } else {
            log.warn("IllegalArgumentException: Support only String");
            throw new IllegalArgumentException("Support only String");
        }
    }

    public static class Binder implements PropertyBinder {
        @SuppressWarnings("unchecked")
		@Override
        public void bind(PropertyBindingContext context) {
            String fieldName = context.bridgedElement().name();

            IndexFieldReference<String> fieldRef = context.indexSchemaElement()
                .field(fieldName, f -> f.asString())
                .toReference();

            context.bridge(String.class, new LowerCaseFieldBridge(fieldRef));
        }
    }
}
