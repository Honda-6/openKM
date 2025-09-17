/**
 * OpenKM, Open Document Management System (http://www.openkm.com)
 * Copyright (c) Paco Avila & Josep Llort
 * <p>
 * No bytes were intentionally harmed during the development of this application.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.openkm.module.db.stuff;

import org.hibernate.search.engine.backend.document.DocumentElement;
import org.hibernate.search.engine.backend.document.IndexFieldReference;
import org.hibernate.search.mapper.pojo.bridge.PropertyBridge;
import org.hibernate.search.mapper.pojo.bridge.runtime.PropertyBridgeWriteContext;

import org.hibernate.search.mapper.pojo.bridge.binding.PropertyBindingContext;
import org.hibernate.search.mapper.pojo.bridge.mapping.programmatic.PropertyBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @author pavila
 */
@SuppressWarnings("rawtypes")
public class SetFieldBridge implements PropertyBridge  {
	private static Logger log = LoggerFactory.getLogger(SetFieldBridge.class);

    private final IndexFieldReference<String> fieldReference;

    public SetFieldBridge(IndexFieldReference<String> fieldReference) {
        this.fieldReference = fieldReference;
    }

	@Override
	public void write(DocumentElement target, Object bridgedElement, PropertyBridgeWriteContext context) {
		
		if (bridgedElement instanceof Set<?>) {
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) bridgedElement;

            for (String elto : set) {
                log.debug("Added field '{}' with value '{}'", fieldReference, elto);
                target.addValue(fieldReference, elto);
            }
        } else {
            log.warn("IllegalArgumentException: Support only Set<String>");
            throw new IllegalArgumentException("Support only Set<String>");
		}

	}
    
public static class SetFieldBridgeBinder implements PropertyBinder {

    @SuppressWarnings("unchecked")
    @Override
    public void bind(PropertyBindingContext context) {
        String fieldName = context.bridgedElement().name();

        if ("keywords".equals(fieldName)) {
            fieldName = "keyword";
        } else if ("categories".equals(fieldName)) {
            fieldName = "category";
        } else if ("subscriptors".equals(fieldName)) {
            fieldName = "subscriptor";
        }

        IndexFieldReference<String> fieldRef = context.indexSchemaElement()
            .field(fieldName, f -> f.asString())
            .toReference();

        context.bridge(Set.class, new SetFieldBridge(fieldRef));
    }
}

}
