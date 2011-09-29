package org.springframework.roo.addon.dbre;

import static org.springframework.roo.model.JdkJavaType.DATE;
import static org.springframework.roo.model.JdkJavaType.SET;
import static org.springframework.roo.model.JpaJavaType.CASCADE_TYPE;
import static org.springframework.roo.model.JpaJavaType.COLUMN;
import static org.springframework.roo.model.JpaJavaType.JOIN_COLUMN;
import static org.springframework.roo.model.JpaJavaType.JOIN_COLUMNS;
import static org.springframework.roo.model.JpaJavaType.JOIN_TABLE;
import static org.springframework.roo.model.JpaJavaType.LOB;
import static org.springframework.roo.model.JpaJavaType.MANY_TO_MANY;
import static org.springframework.roo.model.JpaJavaType.MANY_TO_ONE;
import static org.springframework.roo.model.JpaJavaType.ONE_TO_MANY;
import static org.springframework.roo.model.JpaJavaType.ONE_TO_ONE;
import static org.springframework.roo.model.JpaJavaType.TEMPORAL;
import static org.springframework.roo.model.JpaJavaType.TEMPORAL_TYPE;
import static org.springframework.roo.model.Jsr303JavaType.NOT_NULL;
import static org.springframework.roo.model.RooJavaType.ROO_TO_STRING;
import static org.springframework.roo.model.SpringJavaType.DATE_TIME_FORMAT;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jvnet.inflector.Noun;
import org.springframework.roo.addon.dbre.model.CascadeAction;
import org.springframework.roo.addon.dbre.model.Column;
import org.springframework.roo.addon.dbre.model.Database;
import org.springframework.roo.addon.dbre.model.ForeignKey;
import org.springframework.roo.addon.dbre.model.Reference;
import org.springframework.roo.addon.dbre.model.Table;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.FieldMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.ArrayAttributeValue;
import org.springframework.roo.classpath.details.annotations.EnumAttributeValue;
import org.springframework.roo.classpath.details.annotations.NestedAnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.StringAttributeValue;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.operations.Cardinality;
import org.springframework.roo.classpath.operations.jsr303.SetField;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.DataType;
import org.springframework.roo.model.EnumDetails;
import org.springframework.roo.model.ImportRegistrationResolver;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JdkJavaType;
import org.springframework.roo.project.Path;
import org.springframework.roo.support.util.Assert;

/**
 * Metadata for {@link RooDbManaged}.
 * <p>
 * Creates and manages entity relationships, such as many-valued and single-valued associations.
 * <p>
 * One-to-many and one-to-one associations are created based on the following laws:
 * <ul>
 * <li>Primary Key (PK) - Foreign Key (FK) LAW #1: If the foreign key column is part of the primary key (or part of an index) then the relationship between the tables will be one to many (1:M).
 * <li>Primary Key (PK) - Foreign Key (FK) LAW #2: If the foreign key column represents the entire primary key (or the entire index) then the relationship between the tables will be one to one (1:1).
 * </ul>
 * <p>
 * Many-to-many associations are created if a join table is detected. To be identified as a many-to-many join table, the table must have have exactly two primary keys and have exactly two foreign-keys
 * pointing to other entity tables and have no other columns.
 * 
 * @author Alan Stewart
 * @since 1.1
 */
public class DbreMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {
	
	// Constants
	private static final String PROVIDES_TYPE_STRING = DbreMetadata.class.getName();
	private static final String PROVIDES_TYPE = MetadataIdentificationUtils.create(PROVIDES_TYPE_STRING);
	private static final String NAME = "name";
	private static final String VALUE = "value";
	private static final String MAPPED_BY = "mappedBy";

	// Fields
	private DbManagedAnnotationValues annotationValues;
	private IdentifierHolder identifierHolder;
	private FieldMetadata versionField;
	private Iterable<ClassOrInterfaceTypeDetails> managedEntities;
	private Database database;
	private ClassOrInterfaceTypeDetailsBuilder updatedGovernorBuilder;

	public DbreMetadata(String identifier, JavaType aspectName, PhysicalTypeMetadata governorPhysicalTypeMetadata, DbManagedAnnotationValues annotationValues, IdentifierHolder identifierHolder, FieldMetadata versionField, Iterable<ClassOrInterfaceTypeDetails> managedEntities, Database database) {
		super(identifier, aspectName, governorPhysicalTypeMetadata);
		Assert.isTrue(isValid(identifier), "Metadata identification string '" + identifier + "' does not appear to be a valid");
		Assert.notNull(annotationValues, "Annotation values required");
		Assert.notNull(identifierHolder, "Identifier holder required");
		Assert.notNull(managedEntities, "Managed entities required");
		Assert.notNull(database, "Database required");

		this.annotationValues = annotationValues;
		this.identifierHolder = identifierHolder;
		this.versionField = versionField;
		this.managedEntities = managedEntities;
		this.database = database;

		Table table = this.database.getTable(DbreTypeUtils.getTableName(governorTypeDetails), DbreTypeUtils.getSchemaName(governorTypeDetails));
		if (table == null) {
			valid = false;
			return;
		}

		// Add fields for many-valued associations with many-to-many multiplicity
		addManyToManyFields(table);

		// Add fields for single-valued associations to other entities that have one-to-one multiplicity
		addOneToOneFields(table);

		// Add fields for many-valued associations with one-to-many multiplicity
		addOneToManyFields(table);

		// Add fields for single-valued associations to other entities that have many-to-one multiplicity
		addManyToOneFields(table);

		// Add remaining fields from columns
		addOtherFields(table);
		
		// Create a representation of the desired output ITD
		itdTypeDetails = builder.build();
	}

	public ClassOrInterfaceTypeDetails getUpdatedGovernor() {
		return updatedGovernorBuilder == null ? null : updatedGovernorBuilder.build();
	}

	private void addManyToManyFields(Table table) {
		Map<Table, Integer> owningSideTables = new LinkedHashMap<Table, Integer>();

		for (Table joinTable : database.getTables()) {
			if (!joinTable.isJoinTable()) {
				continue;
			}
			
			String errMsg = "table in join table '" + joinTable.getName() + "' for many-to-many relationship could not be found. Note that table names are case sensitive in some databases such as MySQL.";
			Iterator<ForeignKey> iter = joinTable.getImportedKeys().iterator();
			ForeignKey foreignKey1 = iter.next(); // First foreign key in set
			ForeignKey foreignKey2 = iter.next(); // Second and last foreign key in set

			Table owningSideTable = foreignKey1.getForeignTable();
			Assert.notNull(owningSideTable, "Owning-side " + errMsg);

			Table inverseSideTable = foreignKey2.getForeignTable();
			Assert.notNull(inverseSideTable, "Inverse-side " + errMsg);

			Integer tableCount = owningSideTables.containsKey(owningSideTable) ? owningSideTables.get(owningSideTable) + 1 : 0;
			owningSideTables.put(owningSideTable, tableCount);
			String fieldSuffix = owningSideTables.get(owningSideTable) > 0 ? String.valueOf(owningSideTables.get(owningSideTable)) : "";

			boolean sameTable = owningSideTable.equals(inverseSideTable);

			if (owningSideTable.equals(table)) {
				JavaSymbolName fieldName = new JavaSymbolName(getInflectorPlural(DbreTypeUtils.suggestFieldName(inverseSideTable)) + (sameTable ? "1" : fieldSuffix));
				FieldMetadata field = getManyToManyOwningSideField(fieldName, joinTable, inverseSideTable, foreignKey1.getOnUpdate(), foreignKey1.getOnDelete());
				addToBuilder(field);
			}

			if (inverseSideTable.equals(table)) {
				JavaSymbolName fieldName = new JavaSymbolName(getInflectorPlural(DbreTypeUtils.suggestFieldName(owningSideTable)) + (sameTable ? "2" : fieldSuffix));
				JavaSymbolName mappedByFieldName = new JavaSymbolName(getInflectorPlural(DbreTypeUtils.suggestFieldName(inverseSideTable)) + (sameTable ? "1" : fieldSuffix));
				FieldMetadata field = getManyToManyInverseSideField(fieldName, mappedByFieldName, owningSideTable, foreignKey2.getOnUpdate(), foreignKey2.getOnDelete());
				addToBuilder(field);
			}
		}
	}

	private void addOneToOneFields(Table table) {
		// Add unique one-to-one fields
		Map<JavaSymbolName, FieldMetadata> uniqueFields = new LinkedHashMap<JavaSymbolName, FieldMetadata>();

		for (ForeignKey foreignKey : table.getImportedKeys()) {
			if (!isOneToOne(table, foreignKey)) {
				continue;
			}
			
			Table importedKeyForeignTable = foreignKey.getForeignTable();
			String foreignTableName = importedKeyForeignTable.getName();
			String foreignSchemaName = importedKeyForeignTable.getSchema().getName();
			Short keySequence = foreignKey.getKeySequence();
			String fieldSuffix = keySequence != null && keySequence > 0 ? String.valueOf(keySequence) : "";
			JavaSymbolName fieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(foreignTableName) + fieldSuffix);
			JavaType fieldType = DbreTypeUtils.findTypeForTableName(managedEntities, foreignTableName, foreignSchemaName);
			Assert.notNull(fieldType, "Attempted to create one-to-one field '" + fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "'" + getErrorMsg(importedKeyForeignTable.getFullyQualifiedTableName(), table.getFullyQualifiedTableName()));

			// Fields are stored in a field-keyed map first before adding them to the builder.
			// This ensures the fields from foreign keys with multiple columns will only get created once.
			FieldMetadata field = getOneToOneOrManyToOneField(fieldName, fieldType, foreignKey, ONE_TO_ONE, false);
			uniqueFields.put(fieldName, field);
		}

		for (FieldMetadata field : uniqueFields.values()) {
			addToBuilder(field);

			// Exclude these fields in @RooToString to avoid circular references - ROO-1399
			excludeFieldsInToStringAnnotation(field.getFieldName().getSymbolName());
		}

		// Add one-to-one mapped-by fields
		if (table.isJoinTable()) {
			return;
		}

		for (ForeignKey exportedKey : table.getExportedKeys()) {
			Table exportedKeyForeignTable = exportedKey.getForeignTable();
			Assert.notNull(exportedKeyForeignTable, "Foreign key table for foreign key '" + exportedKey.getName() + "' in table '" + table.getFullyQualifiedTableName() + "' does not exist. One-to-one relationship not created");
			if (exportedKeyForeignTable.isJoinTable()) {
				continue;
			}

			String foreignTableName = exportedKeyForeignTable.getName();
			String foreignSchemaName = exportedKeyForeignTable.getSchema().getName();
			Table foreignTable = database.getTable(foreignTableName, foreignSchemaName);
			Assert.notNull(foreignTable , "Related table '" + exportedKeyForeignTable.getFullyQualifiedTableName() + "' could not be found but has a foreign-key reference to table '" + table.getFullyQualifiedTableName() + "'");
			if (!isOneToOne(foreignTable, foreignTable.getImportedKey(exportedKey.getName()))) {
				continue;
			}
			Short keySequence = exportedKey.getKeySequence();
			String fieldSuffix = keySequence != null && keySequence > 0 ? String.valueOf(keySequence) : "";
			JavaSymbolName fieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(foreignTableName) + fieldSuffix);

			JavaType fieldType = DbreTypeUtils.findTypeForTableName(managedEntities, foreignTableName, foreignSchemaName);
			Assert.notNull(fieldType, "Attempted to create one-to-one mapped-by field '"+ fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "'" + getErrorMsg(foreignTable.getFullyQualifiedTableName()));
			
			// Check for existence of same field - ROO-1691
			while (true) {
				if (!hasFieldInItd(fieldName)) {
					break;
				}
				fieldName = new JavaSymbolName(fieldName.getSymbolName() + "_");
			}

			JavaSymbolName mappedByFieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(table.getName()) + fieldSuffix);

			FieldMetadata field = getOneToOneMappedByField(fieldName, fieldType, mappedByFieldName, exportedKey.getOnUpdate(), exportedKey.getOnDelete());
			addToBuilder(field);
		}
	}

	private void addOneToManyFields(Table table) {
		Assert.notNull(table, "Table required");
		if (table.isJoinTable()) {
			return;
		}
		
		for (ForeignKey exportedKey : table.getExportedKeys()) {
			Table exportedKeyForeignTable = exportedKey.getForeignTable();
			Assert.notNull(exportedKeyForeignTable, "Foreign key table for foreign key '" + exportedKey.getName() + "' in table '" + table.getFullyQualifiedTableName() + "' does not exist. One-to-many relationship not created");
			if (exportedKeyForeignTable.isJoinTable()) {
				continue;
			}

			String foreignTableName = exportedKeyForeignTable.getName();
			String foreignSchemaName = exportedKeyForeignTable.getSchema().getName();
			Table foreignTable = database.getTable(foreignTableName, foreignSchemaName);
			Assert.notNull(foreignTable, "Related table '" + exportedKeyForeignTable.getFullyQualifiedTableName() + "' could not be found but was referenced by table '" + table.getFullyQualifiedTableName() + "'");
			
			if (isOneToOne(foreignTable, foreignTable.getImportedKey(exportedKey.getName()))) {
				continue;
			}
			
			Short keySequence = exportedKey.getKeySequence();
			String fieldSuffix = keySequence != null && keySequence > 0 ? String.valueOf(keySequence) : "";
			JavaSymbolName fieldName = new JavaSymbolName(getInflectorPlural(DbreTypeUtils.suggestFieldName(foreignTableName)) + fieldSuffix);
			JavaSymbolName mappedByFieldName = null;
			if (exportedKey.getReferenceCount() == 1) {
				Reference reference = exportedKey.getReferences().iterator().next();
				mappedByFieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(reference.getForeignColumnName()));
			} else {
				mappedByFieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(table) + fieldSuffix);
			}

			// Check for existence of same field - ROO-1691
			while (true) {
				if (!hasFieldInItd(fieldName)) {
					break;
				}
				fieldName = new JavaSymbolName(fieldName.getSymbolName() + "_");
			}

			FieldMetadata field = getOneToManyMappedByField(fieldName, mappedByFieldName, foreignTableName, foreignSchemaName, exportedKey.getOnUpdate(), exportedKey.getOnDelete());
			addToBuilder(field);
		}
	}

	private void addManyToOneFields(Table table) {
		// Add unique many-to-one fields
		Map<JavaSymbolName, FieldMetadata> uniqueFields = new LinkedHashMap<JavaSymbolName, FieldMetadata>();

		for (ForeignKey foreignKey : table.getImportedKeys()) {
			Table foreignTable = foreignKey.getForeignTable();
			if (foreignTable == null || isOneToOne(table, foreignKey)) {
				continue;
			}
			
			// Assume many-to-one multiplicity
			JavaSymbolName fieldName = null;
			String foreignTableName = foreignTable.getName();
			String foreignSchemaName = foreignTable.getSchema().getName();
			if (foreignKey.getReferenceCount() == 1) {
				Reference reference = foreignKey.getReferences().iterator().next();
				fieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(reference.getLocalColumnName()));
			} else {
				Short keySequence = foreignKey.getKeySequence();
				String fieldSuffix = keySequence != null && keySequence > 0 ? String.valueOf(keySequence) : "";
				fieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(foreignTableName) + fieldSuffix);
			}
			JavaType fieldType = DbreTypeUtils.findTypeForTableName(managedEntities, foreignTableName, foreignSchemaName);
			Assert.notNull(fieldType, "Attempted to create many-to-one field '"+ fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "'" + getErrorMsg(foreignTable.getFullyQualifiedTableName(), table.getFullyQualifiedTableName()));

			// Fields are stored in a field-keyed map first before adding them to the builder.
			// This ensures the fields from foreign keys with multiple columns will only get created once.
			FieldMetadata field = getOneToOneOrManyToOneField(fieldName, fieldType, foreignKey, MANY_TO_ONE, true);
			uniqueFields.put(fieldName, field);
		}

		for (FieldMetadata field : uniqueFields.values()) {
			addToBuilder(field);
		}
	}

	private FieldMetadata getManyToManyOwningSideField(JavaSymbolName fieldName, Table joinTable, Table inverseSideTable, CascadeAction onUpdate, CascadeAction onDelete) {
		JavaType element = DbreTypeUtils.findTypeForTable(managedEntities, inverseSideTable);
		Assert.notNull(element, "Attempted to create many-to-many owning-side field '"+ fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "' " + getErrorMsg(inverseSideTable.getFullyQualifiedTableName()));
		
		List<JavaType> params = Arrays.asList(element);
		String physicalTypeIdentifier = PhysicalTypeIdentifier.createIdentifier(element, Path.SRC_MAIN_JAVA);
		SetField fieldDetails = new SetField(physicalTypeIdentifier, new JavaType(SET.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, params), fieldName, element, Cardinality.MANY_TO_MANY);

		// Add annotations to field
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();

		// Add @ManyToMany annotation
		AnnotationMetadataBuilder manyToManyBuilder = new AnnotationMetadataBuilder(MANY_TO_MANY);
		annotations.add(manyToManyBuilder);

		// Add @JoinTable annotation
		AnnotationMetadataBuilder joinTableBuilder = new AnnotationMetadataBuilder(JOIN_TABLE);
		List<AnnotationAttributeValue<?>> joinTableAnnotationAttributes = new ArrayList<AnnotationAttributeValue<?>>();
		joinTableAnnotationAttributes.add(new StringAttributeValue(new JavaSymbolName(NAME), joinTable.getName()));

		Iterator<ForeignKey> iter = joinTable.getImportedKeys().iterator();

		// Add "joinColumns" attribute containing nested @JoinColumn annotations
		List<NestedAnnotationAttributeValue> joinColumnArrayValues = new ArrayList<NestedAnnotationAttributeValue>();
		Set<Reference> firstKeyReferences = iter.next().getReferences();
		for (Reference reference : firstKeyReferences) {
			AnnotationMetadataBuilder joinColumnBuilder = getJoinColumnAnnotation(reference, (firstKeyReferences.size() > 1));
			joinColumnArrayValues.add(new NestedAnnotationAttributeValue(new JavaSymbolName(VALUE), joinColumnBuilder.build()));
		}
		joinTableAnnotationAttributes.add(new ArrayAttributeValue<NestedAnnotationAttributeValue>(new JavaSymbolName("joinColumns"), joinColumnArrayValues));

		// Add "inverseJoinColumns" attribute containing nested @JoinColumn annotations
		List<NestedAnnotationAttributeValue> inverseJoinColumnArrayValues = new ArrayList<NestedAnnotationAttributeValue>();
		Set<Reference> lastKeyReferences = iter.next().getReferences();
		for (Reference reference : lastKeyReferences) {
			AnnotationMetadataBuilder joinColumnBuilder = getJoinColumnAnnotation(reference, (lastKeyReferences.size() > 1));
			inverseJoinColumnArrayValues.add(new NestedAnnotationAttributeValue(new JavaSymbolName(VALUE), joinColumnBuilder.build()));
		}
		joinTableAnnotationAttributes.add(new ArrayAttributeValue<NestedAnnotationAttributeValue>(new JavaSymbolName("inverseJoinColumns"), inverseJoinColumnArrayValues));

		// Add attributes to a @JoinTable annotation builder
		joinTableBuilder.setAttributes(joinTableAnnotationAttributes);
		annotations.add(joinTableBuilder);

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldDetails.getFieldName(), fieldDetails.getFieldType());
		return fieldBuilder.build();
	}

	private FieldMetadata getManyToManyInverseSideField(JavaSymbolName fieldName, JavaSymbolName mappedByFieldName, Table owningSideTable, CascadeAction onUpdate, CascadeAction onDelete) {
		JavaType element = DbreTypeUtils.findTypeForTable(managedEntities, owningSideTable);
		Assert.notNull(element, "Attempted to create many-to-many inverse-side field '"+ fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "'" + getErrorMsg(owningSideTable.getFullyQualifiedTableName()));

		List<JavaType> params = Arrays.asList(element);
		String physicalTypeIdentifier = PhysicalTypeIdentifier.createIdentifier(element, Path.SRC_MAIN_JAVA);
		SetField fieldDetails = new SetField(physicalTypeIdentifier, new JavaType(SET.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, params), fieldName, element, Cardinality.MANY_TO_MANY);

		// Add annotations to field
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		AnnotationMetadataBuilder manyToManyBuilder = new AnnotationMetadataBuilder(MANY_TO_MANY);
		manyToManyBuilder.addStringAttribute(MAPPED_BY, mappedByFieldName.getSymbolName());
		addCascadeType(manyToManyBuilder, onUpdate, onDelete);
		annotations.add(manyToManyBuilder);

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldDetails.getFieldName(), fieldDetails.getFieldType());
		return fieldBuilder.build();
	}

	private FieldMetadata getOneToOneMappedByField(JavaSymbolName fieldName, JavaType fieldType, JavaSymbolName mappedByFieldName, CascadeAction onUpdate, CascadeAction onDelete) {
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		AnnotationMetadataBuilder oneToOneBuilder = new AnnotationMetadataBuilder(ONE_TO_ONE);
		oneToOneBuilder.addStringAttribute(MAPPED_BY, mappedByFieldName.getSymbolName());
		addCascadeType(oneToOneBuilder, onUpdate, onDelete);
		annotations.add(oneToOneBuilder);

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldName, fieldType);
		return fieldBuilder.build();
	}

	private FieldMetadata getOneToOneOrManyToOneField(JavaSymbolName fieldName, JavaType fieldType, ForeignKey foreignKey, JavaType annotationType, boolean referencedColumn) {
		// Add annotations to field
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();

		// Add annotation
		AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(annotationType);
		if (foreignKey.isExported()) {
			addCascadeType(annotationBuilder, foreignKey.getOnUpdate(), foreignKey.getOnDelete());
		}
		annotations.add(annotationBuilder);

		Set<Reference> references = foreignKey.getReferences();
		if (references.size() == 1) {
			// Add @JoinColumn annotation
			annotations.add(getJoinColumnAnnotation(references.iterator().next(), referencedColumn, fieldType));
		} else {
			// Add @JoinColumns annotation
			annotations.add(getJoinColumnsAnnotation(references, fieldType));
		}

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldName, fieldType);
		return fieldBuilder.build();
	}

	private void excludeFieldsInToStringAnnotation(String fieldName) {
		AnnotationMetadata toStringAnnotation = governorTypeDetails.getAnnotation(ROO_TO_STRING);
		if (toStringAnnotation == null) {
			return;
		}

		List<AnnotationAttributeValue<?>> attributes = new ArrayList<AnnotationAttributeValue<?>>();
		List<StringAttributeValue> ignoreFields = new ArrayList<StringAttributeValue>();

		// Copy the existing attributes, excluding the "ignoreFields" attribute
		boolean alreadyAdded = false;
		AnnotationAttributeValue<?> value = toStringAnnotation.getAttribute(new JavaSymbolName("excludeFields"));
		if (value == null) {
			return;
		}

		// Ensure we have an array of strings
		final String errMsg = "@RooToString attribute 'excludeFields' must be an array of strings";
		Assert.isInstanceOf(ArrayAttributeValue.class, value, errMsg);
		ArrayAttributeValue<?> arrayVal = (ArrayAttributeValue<?>) value;
		for (Object obj : arrayVal.getValue()) {
			Assert.isInstanceOf(StringAttributeValue.class, obj, errMsg);
			StringAttributeValue sv = (StringAttributeValue) obj;
			if (sv.getValue().equals(fieldName)) {
				alreadyAdded = true;
			}
			ignoreFields.add(sv);
		}

		// Add the desired field to ignore to the end
		if (!alreadyAdded) {
			ignoreFields.add(new StringAttributeValue(new JavaSymbolName("ignored"), fieldName));
		}

		attributes.add(new ArrayAttributeValue<StringAttributeValue>(new JavaSymbolName("excludeFields"), ignoreFields));
		AnnotationMetadataBuilder toStringAnnotationBuilder = new AnnotationMetadataBuilder(ROO_TO_STRING, attributes);
		updatedGovernorBuilder = new ClassOrInterfaceTypeDetailsBuilder(governorTypeDetails);
		updatedGovernorBuilder.updateTypeAnnotation(toStringAnnotationBuilder.build(), new HashSet<JavaSymbolName>());
	}

	private FieldMetadata getOneToManyMappedByField(JavaSymbolName fieldName, JavaSymbolName mappedByFieldName, String foreignTableName, String foreignSchemaName, CascadeAction onUpdate, CascadeAction onDelete) {
		JavaType element = DbreTypeUtils.findTypeForTableName(managedEntities, foreignTableName, foreignSchemaName);
		Assert.notNull(element , "Attempted to create one-to-many mapped-by field '"+ fieldName + "' in '" + destination.getFullyQualifiedTypeName() + "'" + getErrorMsg(foreignTableName + "." + foreignSchemaName));

		List<JavaType> params = Arrays.asList(element);
		String physicalTypeIdentifier = PhysicalTypeIdentifier.createIdentifier(element, Path.SRC_MAIN_JAVA);
		SetField fieldDetails = new SetField(physicalTypeIdentifier, new JavaType(SET.getFullyQualifiedTypeName(), 0, DataType.TYPE, null, params), fieldName, element, Cardinality.ONE_TO_MANY);

		// Add @OneToMany annotation
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
		AnnotationMetadataBuilder oneToManyBuilder = new AnnotationMetadataBuilder(ONE_TO_MANY);
		oneToManyBuilder.addStringAttribute(MAPPED_BY, mappedByFieldName.getSymbolName());
		addCascadeType(oneToManyBuilder, onUpdate, onDelete);
		annotations.add(oneToManyBuilder);

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldDetails.getFieldName(), fieldDetails.getFieldType());
		return fieldBuilder.build();
	}

	private AnnotationMetadataBuilder getJoinColumnAnnotation(Reference reference, boolean referencedColumn) {
		return getJoinColumnAnnotation(reference, referencedColumn, null);
	}

	private AnnotationMetadataBuilder getJoinColumnAnnotation(Reference reference, boolean referencedColumn, JavaType fieldType) {
		Column localColumn = reference.getLocalColumn();
		Assert.notNull(localColumn, "Foreign-key reference local column '" + reference.getLocalColumnName() + "' must not be null");
		AnnotationMetadataBuilder joinColumnBuilder = new AnnotationMetadataBuilder(JOIN_COLUMN);
		joinColumnBuilder.addStringAttribute(NAME, localColumn.getEscapedName());

		if (referencedColumn) {
			Column foreignColumn = reference.getForeignColumn();
			Assert.notNull(foreignColumn, "Foreign-key reference foreign column '" + reference.getForeignColumnName() + "' must not be null");
			joinColumnBuilder.addStringAttribute("referencedColumnName", foreignColumn.getEscapedName());
		}

		if (localColumn.isRequired()) {
			joinColumnBuilder.addBooleanAttribute("nullable", false);
		}

		if (fieldType != null) {
			if (isCompositeKeyColumn(localColumn) || localColumn.isPrimaryKey() || !reference.isInsertableOrUpdatable()) {
				joinColumnBuilder.addBooleanAttribute("insertable", false);
				joinColumnBuilder.addBooleanAttribute("updatable", false);
			}
		}

		return joinColumnBuilder;
	}

	private AnnotationMetadataBuilder getJoinColumnsAnnotation(Set<Reference> references, JavaType fieldType) {
		List<NestedAnnotationAttributeValue> arrayValues = new ArrayList<NestedAnnotationAttributeValue>();
		for (Reference reference : references) {
			AnnotationMetadataBuilder joinColumnAnnotation = getJoinColumnAnnotation(reference, true, fieldType);
			arrayValues.add(new NestedAnnotationAttributeValue(new JavaSymbolName(VALUE), joinColumnAnnotation.build()));
		}
		List<AnnotationAttributeValue<?>> attributes = new ArrayList<AnnotationAttributeValue<?>>();
		attributes.add(new ArrayAttributeValue<NestedAnnotationAttributeValue>(new JavaSymbolName(VALUE), arrayValues));
		return new AnnotationMetadataBuilder(JOIN_COLUMNS, attributes);
	}

	private boolean isOneToOne(Table table, ForeignKey foreignKey) {
		Assert.notNull(table, "Table must not be null in determining a one-to-one relationship");
		Assert.notNull(foreignKey, "Foreign key must not be null in determining a one-to-one relationship");
		boolean equals = table.getPrimaryKeyCount() == foreignKey.getReferenceCount();
		Iterator<Column> primaryKeyIterator = table.getPrimaryKeys().iterator();
		while (equals && primaryKeyIterator.hasNext()) {
			equals &= foreignKey.hasLocalColumn(primaryKeyIterator.next());
		}
		return equals;
	}
	
	private void addCascadeType(AnnotationMetadataBuilder annotationBuilder, CascadeAction onUpdate, CascadeAction onDelete) {
		final String attributeName = "cascade";
		boolean hasCascadeType = true;
		if (onUpdate == CascadeAction.CASCADE && onDelete == CascadeAction.CASCADE) {
			annotationBuilder.addEnumAttribute(attributeName, CASCADE_TYPE, "ALL");
		} else if (onUpdate == CascadeAction.CASCADE && onDelete != CascadeAction.CASCADE) {
			List<EnumAttributeValue> arrayValues = new ArrayList<EnumAttributeValue>();
			arrayValues.add(new EnumAttributeValue(new JavaSymbolName(attributeName), new EnumDetails(CASCADE_TYPE, new JavaSymbolName("PERSIST"))));
			arrayValues.add(new EnumAttributeValue(new JavaSymbolName(attributeName), new EnumDetails(CASCADE_TYPE, new JavaSymbolName("MERGE"))));
			annotationBuilder.addAttribute(new ArrayAttributeValue<EnumAttributeValue>(new JavaSymbolName(attributeName), arrayValues));
		} else if (onUpdate != CascadeAction.CASCADE && onDelete == CascadeAction.CASCADE) {
			annotationBuilder.addEnumAttribute(attributeName, CASCADE_TYPE.getSimpleTypeName(), "REMOVE");
		} else {
			hasCascadeType = false;
		}
		if (hasCascadeType) {
			final ImportRegistrationResolver imports = builder.getImportRegistrationResolver();
			imports.addImport(CASCADE_TYPE);
		}
	}

	private void addOtherFields(Table table) {
		Map<JavaSymbolName, FieldMetadata> uniqueFields = new LinkedHashMap<JavaSymbolName, FieldMetadata>();

		for (Column column : table.getColumns()) {
			FieldMetadata field = null;
			String columnName = column.getName();
			JavaSymbolName fieldName = new JavaSymbolName(DbreTypeUtils.suggestFieldName(columnName));

			boolean isIdField = isIdField(fieldName) || column.isPrimaryKey();
			boolean isVersionField = isVersionField(fieldName) || columnName.equals("version");
			boolean isCompositeKeyField = isCompositeKeyField(fieldName);
			boolean isForeignKey = table.findImportedKeyByLocalColumnName(columnName) != null;
			if (isIdField || isVersionField || isCompositeKeyField || isForeignKey) {
				continue;
			}

			boolean hasEmbeddedIdField = isEmbeddedIdField(fieldName) && !isCompositeKeyField;
			if (hasEmbeddedIdField) {
				fieldName = governorTypeDetails.getUniqueFieldName(fieldName.getSymbolName(), true);
			}

			field = getField(fieldName, column, table.getName(), table.isIncludeNonPortableAttributes());
			uniqueFields.put(fieldName, field);
		}

		for (FieldMetadata field : uniqueFields.values()) {
			addToBuilder(field);
		}
	}

	private boolean isCompositeKeyField(JavaSymbolName fieldName) {
		if (!identifierHolder.isEmbeddedIdField()) {
			return false;
		}

		for (FieldMetadata field : identifierHolder.getEmbeddedIdentifierFields()) {
			if (field.getFieldName().equals(fieldName)) {
				return true;
			}
		}
		return false;
	}

	private boolean isCompositeKeyColumn(Column column) {
		if (!identifierHolder.isEmbeddedIdField()) {
			return false;
		}

		for (FieldMetadata field : identifierHolder.getEmbeddedIdentifierFields()) {
			for (AnnotationMetadata annotation : field.getAnnotations()) {
				if (!annotation.getAnnotationType().equals(COLUMN)) {
					continue;
				}
				AnnotationAttributeValue<?> nameAttribute = annotation.getAttribute(new JavaSymbolName(NAME));
				if (nameAttribute != null) {
					String name = (String) nameAttribute.getValue();
					if (column.getName().equals(name)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean isIdField(JavaSymbolName fieldName) {
		return !identifierHolder.isEmbeddedIdField() && identifierHolder.getIdentifierField().getFieldName().equals(fieldName);
	}

	private boolean isEmbeddedIdField(JavaSymbolName fieldName) {
		return identifierHolder.isEmbeddedIdField() && identifierHolder.getIdentifierField().getFieldName().equals(fieldName);
	}

	private boolean isVersionField(JavaSymbolName fieldName) {
		return versionField != null && versionField.getFieldName().equals(fieldName);
	}

	private FieldMetadata getField(JavaSymbolName fieldName, Column column, String tableName, boolean includeNonPortable) {
		JavaType fieldType = column.getJavaType();
		Assert.notNull(fieldType, "Field type for column '" + column.getName() + "' in table '"+ tableName +"' is null");
		
		// Check if field is a Boolean object and is required, then change to boolean primitive
		if (fieldType.equals(JavaType.BOOLEAN_OBJECT) && column.isRequired()) {
			fieldType = JavaType.BOOLEAN_PRIMITIVE;
		}

		// Add annotations to field
		List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();

		// Add @Column annotation
		AnnotationMetadataBuilder columnBuilder = new AnnotationMetadataBuilder(COLUMN);
		columnBuilder.addStringAttribute(NAME, column.getEscapedName());
		if (includeNonPortable) {
			columnBuilder.addStringAttribute("columnDefinition", column.getTypeName());
		}

		// Add length attribute for Strings
		if (column.getColumnSize() < 4000 && fieldType.equals(JavaType.STRING)) {
			columnBuilder.addIntegerAttribute("length", column.getColumnSize());
		}

		// Add precision and scale attributes for numeric fields
		if (column.getScale() > 0 && JdkJavaType.isDecimalType(fieldType)) {
			columnBuilder.addIntegerAttribute("precision", column.getColumnSize());
			columnBuilder.addIntegerAttribute("scale", column.getScale());
		}

		// Add unique = true to @Column if applicable
		if (column.isUnique()) {
			columnBuilder.addBooleanAttribute("unique", true);
		}

		annotations.add(columnBuilder);

		// Add @NotNull if applicable
		if (column.isRequired()) {
			annotations.add(new AnnotationMetadataBuilder(NOT_NULL));
		}

		// Add JSR 220 @Temporal annotation to date fields
		if (fieldType.equals(DATE)) {
			AnnotationMetadataBuilder temporalBuilder = new AnnotationMetadataBuilder(TEMPORAL);
			temporalBuilder.addEnumAttribute(VALUE, new EnumDetails(TEMPORAL_TYPE, new JavaSymbolName(column.getJdbcType())));
			annotations.add(temporalBuilder);

			AnnotationMetadataBuilder dateTimeFormatBuilder = new AnnotationMetadataBuilder(DATE_TIME_FORMAT);
			dateTimeFormatBuilder.addStringAttribute("style", "M-");
			annotations.add(dateTimeFormatBuilder);
		}

		// Add @Lob for CLOB fields if applicable
		if (column.getJdbcType().equals("CLOB")) {
			annotations.add(new AnnotationMetadataBuilder(LOB));
		}

		FieldMetadataBuilder fieldBuilder = new FieldMetadataBuilder(getId(), Modifier.PRIVATE, annotations, fieldName, fieldType);
		return fieldBuilder.build();
	}

	private void addToBuilder(FieldMetadata field) {
		if (field == null || hasField(field)) {
			return;
		}

		builder.addField(field);

		// Check for an existing accessor in the governor 
		builder.addMethod(getAccessorMethod(field.getFieldName(), field.getFieldType()));

		// Check for an existing mutator in the governor 
		builder.addMethod(getMutatorMethod(field.getFieldName(), field.getFieldType()));
	}

	private boolean hasField(FieldMetadata field) {
		// Check governor for field
		if (governorTypeDetails.getField(field.getFieldName()) != null) {
			return true;
		}

		// Check @Column and @JoinColumn annotations on fields in governor with the same 'name' as the generated field 
		final List<FieldMetadata> governorFields = governorTypeDetails.getFieldsWithAnnotation(COLUMN);
		governorFields.addAll(governorTypeDetails.getFieldsWithAnnotation(JOIN_COLUMN));
		for (FieldMetadata governorField : governorFields) {
			governorFieldAnnotations: for (AnnotationMetadata governorFieldAnnotation : governorField.getAnnotations()) {
				if (governorFieldAnnotation.getAnnotationType().equals(COLUMN) || governorFieldAnnotation.getAnnotationType().equals(JOIN_COLUMN)) {
					AnnotationAttributeValue<?> name = governorFieldAnnotation.getAttribute(new JavaSymbolName(NAME));
					if (name == null) {
						continue governorFieldAnnotations;
					}
					for (AnnotationMetadata annotationMetadata : field.getAnnotations()) {
						AnnotationAttributeValue<?> columnName = annotationMetadata.getAttribute(new JavaSymbolName(NAME));
						if (columnName != null && columnName.equals(name)) {
							return true;
						}
					}
				}
			}
		}

		return false;
	}

	/**
	 * Indicates whether the ITD being built has a field of the given name
	 * 
	 * @param fieldName
	 * @return true if the field exists in the builder, otherwise false
	 */
	private boolean hasFieldInItd(final JavaSymbolName fieldName) {
		for (final FieldMetadataBuilder declaredField : builder.getDeclaredFields()) {
			if (declaredField.getFieldName().equals(fieldName)) {
				return true;
			}
		}
		return false;
	}

	private String getInflectorPlural(String term) {
		try {
			return Noun.pluralOf(term, Locale.ENGLISH);
		} catch (RuntimeException e) {
			// Inflector failed (see for example ROO-305), so don't pluralize it
			return term;
		}
	}

	private String getErrorMsg(String tableName) {
		return " but type for table '" + tableName + "' could not be found or is not database managed (not annotated with @RooDbManaged)";
	}

	private String getErrorMsg(String foreignTableName, String tableName) {
		return getErrorMsg(foreignTableName) + " and table '" + tableName + "' has a foreign-key reference to table '" + foreignTableName + "'";
	}

	public boolean isAutomaticallyDelete() {
		return annotationValues.isAutomaticallyDelete();
	}
	
	public static String getMetadataIdentiferType() {
		return PROVIDES_TYPE;
	}

	public static String createIdentifier(JavaType javaType, Path path) {
		return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
	}

	public static JavaType getJavaType(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static Path getPath(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static boolean isValid(String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}
}
