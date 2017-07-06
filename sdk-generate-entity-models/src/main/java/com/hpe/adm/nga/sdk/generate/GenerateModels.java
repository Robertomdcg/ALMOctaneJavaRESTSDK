package com.hpe.adm.nga.sdk.generate;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.authentication.SimpleClientAuthentication;
import com.hpe.adm.nga.sdk.metadata.EntityMetadata;
import com.hpe.adm.nga.sdk.metadata.FieldMetadata;
import com.hpe.adm.nga.sdk.metadata.Metadata;
import com.hpe.adm.nga.sdk.metadata.features.Feature;
import com.hpe.adm.nga.sdk.metadata.features.RestFeature;
import com.hpe.adm.nga.sdk.metadata.features.SubTypesOfFeature;
import com.hpe.adm.nga.sdk.model.EntityModel;
import com.hpe.adm.nga.sdk.model.ReferenceFieldModel;
import com.hpe.adm.nga.sdk.model.StringFieldModel;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by brucesp on 21-Jun-17.
 */
public class GenerateModels {

    private final Template template, interfaceTemplate, entityListTemplate, phasesTemplate, listsTemplate;
    private final File modelDirectory, entitiesDirectory, enumsDirectory;

    public GenerateModels(final File outputDirectory) {
        final File packageDirectory = new File(outputDirectory, "/com/hpe/adm/nga/sdk");
        modelDirectory = new File(packageDirectory, "model");
        modelDirectory.mkdirs();
        entitiesDirectory = new File(packageDirectory, "entities");
        entitiesDirectory.mkdirs();
        enumsDirectory = new File(packageDirectory, "enums");
        enumsDirectory.mkdirs();

        final VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty("resource.loader", "class");
        velocityEngine.setProperty("class.resource.loader.description", "Velocity Classpath Resource Loader");
        velocityEngine.setProperty("runtime.log.logsystem.log4j.logger", "root");
        velocityEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");

        velocityEngine.init();

        template = velocityEngine.getTemplate("/EntityModel.vm");
        interfaceTemplate = velocityEngine.getTemplate("/Entity.vm");
        entityListTemplate = velocityEngine.getTemplate("/TypedEntityList.vm");
        phasesTemplate = velocityEngine.getTemplate("/Phases.vm");
        listsTemplate = velocityEngine.getTemplate("/Lists.vm");
    }

    public void generate(String clientId, String clientSecret, String server, long sharedSpace, long workSpace) throws IOException {
        // work around for work_items_root
        final Octane octanePrivate = new Octane.Builder(new SimpleClientAuthentication(clientId, clientSecret, "HPE_REST_API_TECH_PREVIEW")).sharedSpace(sharedSpace).workSpace(workSpace).Server(server).build();
        final EntityMetadata work_items_root = octanePrivate.metadata().entities("work_item_root").execute().iterator().next();
        final Collection<FieldMetadata> work_items_rootFields = octanePrivate.metadata().fields("work_item_root").execute();
        octanePrivate.signOut();

        final Octane octane = new Octane.Builder(new SimpleClientAuthentication(clientId, clientSecret)).sharedSpace(sharedSpace).workSpace(workSpace).Server(server).build();
        final Metadata metadata = octane.metadata();
        final Collection<EntityMetadata> entityMetadata = metadata.entities().execute();
        entityMetadata.add(work_items_root);

        final Map<String, String> logicalNameToListsMap = generateLists(octane);
        final Set<String> availablePhases = generatePhases(octane);

        for (EntityMetadata entityMetadatum : entityMetadata) {
            final String name = entityMetadatum.getName();
            final String interfaceName = GeneratorHelper.camelCaseFieldName(name) + "Entity";
            final Collection<FieldMetadata> fieldMetadata = generateEntity(work_items_rootFields, metadata, entityMetadata, entityMetadatum, name, interfaceName, logicalNameToListsMap, availablePhases);
            generateInterface(entityMetadatum, name, interfaceName);
            generateEntityList(entityMetadatum, name, fieldMetadata);
        }
    }

    private Map<String, String> generateLists(Octane octane) throws IOException {
        final Collection<EntityModel> listNodes = octane.entityList("list_nodes").get().addFields("name", "list_root", "id", "logical_name").execute();
        final Map<String, List<String[]>> mappedListNodes = new HashMap<>();
        final Map<String, String> logicalNameToNameMap = new HashMap<>();
        listNodes.forEach(listNode -> {
            final String rootId;
            final ReferenceFieldModel list_root = (ReferenceFieldModel) listNode.getValue("list_root");
            final EntityModel list_rootValue = list_root.getValue();
            final String name;
            if (list_rootValue != null) {
                rootId = list_rootValue.getId();
                name = ((StringFieldModel) listNode.getValue("name")).getValue().replaceAll(" ", "_").replaceAll("^\\d", "_$0").replaceAll("\\W", "_").toUpperCase();
            } else {
                rootId = listNode.getId();
                name = GeneratorHelper.camelCaseFieldName(((StringFieldModel) listNode.getValue("name")).getValue().replaceAll("\\W", "_"));
                logicalNameToNameMap.put(((StringFieldModel) listNode.getValue("logical_name")).getValue(), name);
            }
            final List<String[]> listHierarchy = mappedListNodes.computeIfAbsent(rootId, k -> new ArrayList<>());

            final String[] listNodeInfo = {name, ((StringFieldModel) listNode.getValue("id")).getValue()};
            if (list_rootValue == null) {
                listHierarchy.add(0, listNodeInfo);
            } else {
                listHierarchy.add(listNodeInfo);
            }
        });

        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("listNodes", mappedListNodes);
        final FileWriter fileWriter = new FileWriter(new File(enumsDirectory, "Lists.java"));
        listsTemplate.merge(velocityContext, fileWriter);
        fileWriter.close();

        return logicalNameToNameMap;
    }

    private Set<String> generatePhases(Octane octane) throws IOException {
        final Map<String, Set<String[]>> phaseMap = new HashMap<>();
        final Collection<EntityModel> phases = octane.entityList("phases").get().addFields("id", "name", "entity").execute();
        phases.forEach(phase -> {
            final Set<String[]> phaseValueSet = new HashSet<>();
            phaseValueSet.add(new String[]{phase.getId(), ((StringFieldModel) phase.getValue("name")).getValue().replaceAll(" ", "_").toUpperCase()});
            phaseMap.merge(GeneratorHelper.camelCaseFieldName(((StringFieldModel) phase.getValue("entity")).getValue(), true), phaseValueSet, (existingValues, newValues) -> {
                existingValues.addAll(newValues);
                return existingValues;
            });
        });

        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("phaseMap", phaseMap);
        final FileWriter fileWriter = new FileWriter(new File(enumsDirectory, "Phases.java"));
        phasesTemplate.merge(velocityContext, fileWriter);
        fileWriter.close();

        return phaseMap.keySet();
    }

    private Collection<FieldMetadata> generateEntity(Collection<FieldMetadata> work_items_rootFields, Metadata metadata, Collection<EntityMetadata> entityMetadata, EntityMetadata entityMetadatum, String name, String interfaceName, Map<String, String> logicalNameToListsMap, Set<String> availablePhases) throws IOException {
        //if (!name.equals("run")) continue;
        System.out.println(name + ":");
        final Collection<FieldMetadata> fieldMetadata = name.equals("work_item_root") ? work_items_rootFields : metadata.fields(name).execute();

        final TreeMap<String, List<String>> collectedReferences = fieldMetadata.stream()
                .filter(FieldMetadata::isRequired)
                .collect(Collectors.toMap(FieldMetadata::getName, fieldMetadata1 -> {
                    final List<String> references = new ArrayList<>();
                    final String className = GeneratorHelper.camelCaseFieldName(entityMetadatum.getName());
                    if (fieldMetadata1.getName().equals("phase") && availablePhases.contains(className)) {
                        references.add("com.hpe.adm.nga.sdk.enums.Phases." + className + "Phase");
                    } else if (fieldMetadata1.getFieldType() == FieldMetadata.FieldType.Reference) {
                        if ((!entityMetadatum.getName().equals("list_node")) && (fieldMetadata1.getFieldTypedata().getTargets()[0].getType().equals("list_node"))) {
                            final String listName = logicalNameToListsMap.get(fieldMetadata1.getFieldTypedata().getTargets()[0].logicalName());
                            references.add("com.hpe.adm.nga.sdk.enums.Lists." + listName);
                        } else {
                            final GeneratorHelper.ReferenceMetadata referenceMetadata = GeneratorHelper.getAllowedSuperTypesForReference(fieldMetadata1, entityMetadata);
                            if (fieldMetadata1.getFieldTypedata().isMultiple()) {
                                references.add(referenceMetadata.getReferenceClassForSignature());
                            } else {
                                if (referenceMetadata.hasTypedReturn()) {
                                    references.addAll(referenceMetadata.getReferenceTypes()
                                            .stream()
                                            .map(type -> GeneratorHelper.camelCaseFieldName(type).concat("EntityModel"))
                                            .collect(Collectors.toSet()));
                                }
                                if (referenceMetadata.hasNonTypedReturn()) {
                                    references.add("EntityModel");
                                }
                            }
                        }
                    } else {
                        references.add(GeneratorHelper.getFieldTypeAsJava(fieldMetadata1.getFieldType()));
                    }

                    return references;
                }, (strings, strings2) -> {
                    throw new IllegalStateException("problem merging map");
                }, TreeMap::new));

        final Set<List<String[]>> requiredFields = new HashSet<>();
        if (!collectedReferences.isEmpty()) {
            expandCollectedReferences(collectedReferences, new int[collectedReferences.size()], 0, requiredFields);
        }

        final VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("interfaceName", interfaceName);
        velocityContext.put("entityMetadata", entityMetadatum);
        velocityContext.put("fieldMetadata", fieldMetadata);
        velocityContext.put("logicalNameToListsMap", logicalNameToListsMap);
        velocityContext.put("entityMetadataCollection", entityMetadata);
        velocityContext.put("GeneratorHelper", GeneratorHelper.class);
        velocityContext.put("entityMetadataWrapper", GeneratorHelper.entityMetadataWrapper(entityMetadatum));
        velocityContext.put("availablePhases", availablePhases);
        velocityContext.put("requiredFields", requiredFields);

        final FileWriter fileWriter = new FileWriter(new File(modelDirectory, GeneratorHelper.camelCaseFieldName(name) + "EntityModel.java"));
        template.merge(velocityContext, fileWriter);

        fileWriter.close();
        return fieldMetadata;
    }

    private void expandCollectedReferences(final TreeMap<String, List<String>> collectedReferences, final int[] positions, final int pointer, final Set<List<String[]>> output) {
        final Object[] keyArray = collectedReferences.keySet().toArray();
        final Object o = keyArray[pointer];
        for (int i = 0; i < collectedReferences.get(o).size(); ++i) {
            if (pointer == positions.length - 1) {
                final List<String[]> outputLine = new ArrayList<>(positions.length);
                for (int j = 0; j < positions.length; ++j) {
                    outputLine.add(new String[]{(String) keyArray[j], collectedReferences.get(keyArray[j]).get(positions[j])});
                }
                output.add(outputLine);
            } else {
                expandCollectedReferences(collectedReferences, positions, pointer + 1, output);
            }
            positions[pointer]++;
        }
        positions[pointer] = 0;
    }

    private void generateInterface(EntityMetadata entityMetadatum, String name, String interfaceName) throws IOException {
        // interface
        final VelocityContext interfaceVelocityContext = new VelocityContext();
        final Optional<Feature> subTypeOfFeature = entityMetadatum.features().stream().filter(feature -> feature instanceof SubTypesOfFeature).findAny();

        interfaceVelocityContext.put("interfaceName", interfaceName);
        interfaceVelocityContext.put("superInterfaceName",
                (subTypeOfFeature.map(feature -> GeneratorHelper.camelCaseFieldName(((SubTypesOfFeature) feature).getType())).orElse("")) + "Entity");

        final FileWriter interfaceFileWriter = new FileWriter(new File(modelDirectory, GeneratorHelper.camelCaseFieldName(name) + "Entity.java"));
        interfaceTemplate.merge(interfaceVelocityContext, interfaceFileWriter);

        interfaceFileWriter.close();
    }

    private void generateEntityList(EntityMetadata entityMetadatum, String name, Collection<FieldMetadata> fieldMetadata) throws IOException {
        // entityList
        final Optional<Feature> hasRestFeature = entityMetadatum.features().stream()
                .filter(feature -> feature instanceof RestFeature)
                .findFirst();
        // if not then something is wrong!
        if (hasRestFeature.isPresent()) {
            final RestFeature restFeature = (RestFeature) hasRestFeature.get();

            final VelocityContext entityListVelocityContext = new VelocityContext();
            entityListVelocityContext.put("helper", GeneratorHelper.class);
            entityListVelocityContext.put("type", GeneratorHelper.camelCaseFieldName(name));
            entityListVelocityContext.put("url", restFeature.getUrl());
            entityListVelocityContext.put("availableFields", fieldMetadata.stream().map(FieldMetadata::getName).collect(Collectors.toList()));
            entityListVelocityContext.put("sortableFields", fieldMetadata.stream().filter(FieldMetadata::isSortable).map(FieldMetadata::getName).collect(Collectors.toList()));

            final String[] restFeatureMethods = restFeature.getMethods();
            for (String restFeatureMethod : restFeatureMethods) {
                switch (restFeatureMethod) {
                    case "GET":
                        entityListVelocityContext.put("hasGet", true);
                        break;
                    case "POST":
                        entityListVelocityContext.put("hasCreate", true);
                        break;
                    case "PUT":
                        entityListVelocityContext.put("hasUpdate", true);
                        break;
                    case "DELETE":
                        entityListVelocityContext.put("hasDelete", true);
                        break;
                }
            }

            final FileWriter entityListFileWriter = new FileWriter(new File(entitiesDirectory, GeneratorHelper.camelCaseFieldName(name) + "EntityList.java"));
            entityListTemplate.merge(entityListVelocityContext, entityListFileWriter);

            entityListFileWriter.close();
        }
    }
}