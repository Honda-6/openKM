package com.openkm.module.db.stuff;

import com.google.gson.Gson;
import com.openkm.bean.PropertyGroup;
import com.openkm.bean.form.FormElement;
import com.openkm.bean.form.Input;
import com.openkm.bean.form.Select;
import com.openkm.core.Config;
import com.openkm.core.ParseException;
import com.openkm.dao.bean.NodeProperty;
import com.openkm.util.FormUtils;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;
import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@SuppressWarnings("rawtypes")
public class SetPropertiesFieldBridge implements PropertyBridge {

    private static final Logger log = LoggerFactory.getLogger(SetPropertiesFieldBridge.class);
    private final Map<String, IndexFieldReference<String>> fieldReferences;

    public SetPropertiesFieldBridge(Map<String, IndexFieldReference<String>> fieldReferences) {
        this.fieldReferences = fieldReferences;
    }

    @Override
    public void write(DocumentElement target, Object bridgedElement, PropertyBridgeWriteContext context) {
        if (bridgedElement instanceof Set<?>) {
            @SuppressWarnings("unchecked")
            Set<NodeProperty> properties = (Set<NodeProperty>) bridgedElement;

            try {
                Map<PropertyGroup, List<FormElement>> formsElements =
                        FormUtils.parsePropertyGroupsForms(Config.PROPERTY_GROUPS_XML);
                Gson gson = new Gson();

                for (NodeProperty nodeProp : properties) {
                    String propName = nodeProp.getName();
                    String propValue = nodeProp.getValue();

                    if (propValue != null && !propValue.isEmpty()) {
                        FormElement fe = FormUtils.getFormElement(formsElements, propName);
                        IndexFieldReference<String> fieldRef = fieldReferences.get(propName);

                        if (fieldRef == null) {
                            log.warn("No index field reference found for '{}'", propName);
                            continue;
                        }

                        if (fe instanceof Input && ((Input) fe).getType().equals(Input.TYPE_DATE)) {
                            propValue = propValue.substring(0, 8);
                            log.debug("Added date field '{}' with value '{}'", propName, propValue);
                            target.addValue(fieldRef, propValue);
                        } else if (fe instanceof Select) {
                            String[] propValues = gson.fromJson(propValue, String[].class);
                            for (String optValue : propValues) {
                                log.debug("Added list field '{}' with value '{}'", propName, optValue);
                                target.addValue(fieldRef, optValue);
                            }
                        } else {
                            log.debug("Added field '{}' with value '{}'", propName, propValue);
                            target.addValue(fieldRef, propValue);
                        }
                    }
                }
            } catch (ParseException | IOException e) {
                log.error("Error parsing property groups: {}", e.getMessage(), e);
            }
        } else {
            log.warn("IllegalArgumentException: Support only Set<NodeProperty>");
            throw new IllegalArgumentException("Support only Set<NodeProperty>");
        }
    }

    public static class Binder implements PropertyBinder {
        @SuppressWarnings("unchecked")
		@Override
        public void bind(PropertyBindingContext context) {
            Map<String, IndexFieldReference<String>> fieldRefs = new HashMap<>();

            // You may want to dynamically determine field names based on your domain model
            // For now, we assume the field name is the property name
            String fieldName = context.bridgedElement().name();
            IndexFieldReference<String> fieldRef = context.indexSchemaElement()
                    .field(fieldName, f -> f.asString())
                    .toReference();

            fieldRefs.put(fieldName, fieldRef);

            context.bridge(Set.class, new SetPropertiesFieldBridge(fieldRefs));
        }
    }
}
