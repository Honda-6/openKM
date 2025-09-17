package com.openkm.module.db.stuff;

import com.openkm.bean.Permission;
import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;

@SuppressWarnings("rawtypes")
public class MapFieldBridge implements PropertyBridge {

    private static final Logger log = LoggerFactory.getLogger(MapFieldBridge.class);
    private final IndexFieldReference<String> fieldReference;
    private final String fieldName;

    public MapFieldBridge(IndexFieldReference<String> fieldReference, String fieldName) {
        this.fieldReference = fieldReference;
        this.fieldName = fieldName;
    }

    @Override
    public void write(DocumentElement target, Object bridgedElement, PropertyBridgeWriteContext context) {
        if (bridgedElement instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> map = (Map<String, Integer>) bridgedElement;

            for (Entry<String, Integer> entry : map.entrySet()) {
                if ((Permission.READ & entry.getValue()) != 0) {
                    log.debug("Added field '{}' with value '{}'", fieldName, entry.getKey());
                    target.addValue(fieldReference, entry.getKey());
                }
            }
        } else {
            log.warn("IllegalArgumentException: Support only Map<String, Integer>");
            throw new IllegalArgumentException("Support only Map<String, Integer>");
        }
    }

    public static class Binder implements PropertyBinder {
        @SuppressWarnings("unchecked")
		@Override
        public void bind(PropertyBindingContext context) {
            String originalName = context.bridgedElement().name();
            String fieldName;

            if ("userPermissions".equals(originalName)) {
                fieldName = "userPermission";
            } else if ("rolePermissions".equals(originalName)) {
                fieldName = "rolePermission";
            } else {
                fieldName = originalName;
            }

            IndexFieldReference<String> fieldRef = context.indexSchemaElement()
                .field(fieldName, f -> f.asString())
                .toReference();

            context.bridge(Map.class, new MapFieldBridge(fieldRef, fieldName));
        }
    }
}
