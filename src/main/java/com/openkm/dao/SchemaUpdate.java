package com.openkm.dao;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.hibernate.tool.schema.spi.DelayedDropAction;
import org.hibernate.tool.schema.spi.DelayedDropRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Hibernate 6 SchemaUpdate replacement.
 */
public class SchemaUpdate {
    private static final Logger log = LoggerFactory.getLogger(SchemaUpdate.class);
    private final StandardServiceRegistry registry;
    private final Metadata metadata;
    private final List<Exception> exceptions = new ArrayList<>();
    private String outputFile;

    public SchemaUpdate(String cfgFile) {
        registry = new StandardServiceRegistryBuilder()
                .configure(cfgFile != null ? cfgFile : "hibernate.cfg.xml")
                .build();
        metadata = new MetadataSources(registry).buildMetadata();
    }

    public SchemaUpdate setOutputFile(String filename) {
        this.outputFile = filename;
        return this;
    }

    public void execute(boolean script, boolean doUpdate) {
        log.info("Running schema update with Hibernate 6 API");

        try {
            Map<String, Object> settings = new HashMap<>();

            // Control execution behavior via settings
            settings.put("hibernate.hbm2ddl.schema_update", true);
            settings.put("hibernate.hbm2ddl.script", script);
            settings.put("hibernate.hbm2ddl.auto", doUpdate ? "update" : "none");
            if (outputFile != null) {
                settings.put("hibernate.hbm2ddl.import_files_output", outputFile);
            }

            SchemaManagementToolCoordinator.process(
                    metadata,
                    registry,
                    settings,
       				runnable -> { /* ignore */ }

            );

            log.info("Schema update complete");
        } catch (Exception e) {
            exceptions.add(e);
            log.error("Schema update failed", e);
        }
    }

    public List<Exception> getExceptions() {
        return exceptions;
    }

    public static void main(String[] args) {
        try {
            String configFile = null;
            String outFile = null;
            boolean script = true;
            boolean doUpdate = true;

            for (String arg : args) {
                if (arg.startsWith("--config=")) {
                    configFile = arg.substring(9);
                } else if (arg.startsWith("--output=")) {
                    outFile = arg.substring(9);
                } else if (arg.equals("--text")) {
                    doUpdate = false;
                } else if (arg.equals("--quiet")) {
                    script = false;
                }
            }

            new SchemaUpdate(configFile)
                    .setOutputFile(outFile)
                    .execute(script, doUpdate);
        } catch (Exception e) {
            log.error("Error running schema update", e);
        }
    }
}
