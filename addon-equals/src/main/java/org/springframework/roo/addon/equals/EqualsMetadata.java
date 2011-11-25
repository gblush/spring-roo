package org.springframework.roo.addon.equals;

import static org.springframework.roo.model.JavaType.BOOLEAN_PRIMITIVE;
import static org.springframework.roo.model.JavaType.INT_PRIMITIVE;
import static org.springframework.roo.model.JavaType.OBJECT;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import org.springframework.roo.classpath.PhysicalTypeIdentifierNamingUtils;
import org.springframework.roo.classpath.PhysicalTypeMetadata;
import org.springframework.roo.classpath.details.FieldMetadata;
import org.springframework.roo.classpath.details.MethodMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotatedJavaType;
import org.springframework.roo.classpath.itd.AbstractItdTypeDetailsProvidingMetadataItem;
import org.springframework.roo.classpath.itd.InvocableMemberBodyBuilder;
import org.springframework.roo.metadata.MetadataIdentificationUtils;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.support.style.ToStringCreator;
import org.springframework.roo.support.util.Assert;
import org.springframework.roo.support.util.CollectionUtils;

/**
 * Metadata for {@link RooEquals}.
 *
 * @author Alan Stewart
 * @since 1.2.0
 */
public class EqualsMetadata extends AbstractItdTypeDetailsProvidingMetadataItem {

	// Constants
	private static final JavaSymbolName EQUALS_METHOD_NAME = new JavaSymbolName("equals");
	private static final JavaSymbolName HASH_CODE_METHOD_NAME = new JavaSymbolName("hashCode");
	private static final JavaType EQUALS_BUILDER = new JavaType("org.apache.commons.lang3.builder.EqualsBuilder");
	private static final JavaType HASH_CODE_BUILDER = new JavaType("org.apache.commons.lang3.builder.HashCodeBuilder");
	private static final String OBJECT_NAME = "obj";
	private static final String PROVIDES_TYPE_STRING = EqualsMetadata.class.getName();
	private static final String PROVIDES_TYPE = MetadataIdentificationUtils.create(PROVIDES_TYPE_STRING);

	/**
	 * Returns the class-level ID of this type of metadata
	 * 
	 * @return a valid class-level MID
	 */
	public static String getMetadataIdentiferType() {
		return PROVIDES_TYPE;
	}

	public static String createIdentifier(final JavaType javaType, final LogicalPath path) {
		return PhysicalTypeIdentifierNamingUtils.createIdentifier(PROVIDES_TYPE_STRING, javaType, path);
	}

	public static JavaType getJavaType(final String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getJavaType(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static LogicalPath getPath(final String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.getPath(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}

	public static boolean isValid(final String metadataIdentificationString) {
		return PhysicalTypeIdentifierNamingUtils.isValid(PROVIDES_TYPE_STRING, metadataIdentificationString);
	}
	
	// Fields
	private final EqualsAnnotationValues annotationValues;
	private final List<FieldMetadata> locatedFields;

	/**
	 * Constructor
	 *
	 * @param identifier the ID of this piece of metadata (required)
	 * @param aspectName the name of the ITD to generate (required)
	 * @param governorPhysicalTypeMetadata the details of the governor (required)
	 * @param annotationValues the values of the @RooEquals annotation (required)
	 * @param equalityFields the fields to be compared by the <code>equals</code> method (can be <code>null</code> or empty)
	 */
	public EqualsMetadata(final String identifier, final JavaType aspectName, final PhysicalTypeMetadata governorPhysicalTypeMetadata, final EqualsAnnotationValues annotationValues, final List<FieldMetadata> equalityFields) {
		super(identifier, aspectName, governorPhysicalTypeMetadata);
		Assert.isTrue(isValid(identifier), "Metadata id '" + identifier + "' is invalid");
		Assert.notNull(annotationValues, "Annotation values required");

		this.annotationValues = annotationValues;
		this.locatedFields = equalityFields;

		if (!CollectionUtils.isEmpty(equalityFields)) {
			builder.addMethod(getEqualsMethod());
			builder.addMethod(getHashCodeMethod());
		}

		// Create a representation of the desired output ITD
		itdTypeDetails = builder.build();
	}

	/**
	 * Returns the <code>equals</code> method to be generated
	 *
	 * @return <code>null</code> if no generation is required
	 */
	private MethodMetadataBuilder getEqualsMethod() {
		final JavaType parameterType = OBJECT;
		if (getGovernorMethod(EQUALS_METHOD_NAME, parameterType) != null) {
			return null;
		}

		final List<JavaSymbolName> parameterNames = Arrays.asList(new JavaSymbolName(OBJECT_NAME));

		builder.getImportRegistrationResolver().addImport(EQUALS_BUILDER);

		final String typeName = destination.getSimpleTypeName();

		// Create the method
		final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();
		bodyBuilder.appendFormalLine("if (!(" + OBJECT_NAME + " instanceof " + typeName + ")) {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("return false;");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");
		bodyBuilder.appendFormalLine("if (this == " + OBJECT_NAME + ") {");
		bodyBuilder.indent();
		bodyBuilder.appendFormalLine("return true;");
		bodyBuilder.indentRemove();
		bodyBuilder.appendFormalLine("}");
		bodyBuilder.appendFormalLine(typeName + " rhs = (" + typeName + ") " + OBJECT_NAME + ";");

		final StringBuilder builder = new StringBuilder("return new EqualsBuilder()");
		if (annotationValues.isAppendSuper()) {
			builder.append(".appendSuper(super.equals(" + OBJECT_NAME + "))");
		}
		for (final FieldMetadata field : locatedFields) {
			builder.append(".append(" + field.getFieldName() + ", rhs." + field.getFieldName() +")");
		}
		builder.append(".isEquals();");

		bodyBuilder.appendFormalLine(builder.toString());

		return new MethodMetadataBuilder(getId(), Modifier.PUBLIC, EQUALS_METHOD_NAME, BOOLEAN_PRIMITIVE, AnnotatedJavaType.convertFromJavaTypes(parameterType), parameterNames, bodyBuilder);
	}

	/**
	 * Returns the <code>hashCode</code> method to be generated
	 *
	 * @return <code>null</code> if no generation is required
	 */
	private MethodMetadataBuilder getHashCodeMethod() {
		if (getGovernorMethod(HASH_CODE_METHOD_NAME) != null) {
			return null;
		}

		builder.getImportRegistrationResolver().addImport(HASH_CODE_BUILDER);

		// Create the method
		final InvocableMemberBodyBuilder bodyBuilder = new InvocableMemberBodyBuilder();

		final StringBuilder builder = new StringBuilder("return new HashCodeBuilder()");
		if (annotationValues.isAppendSuper()) {
			builder.append(".appendSuper(super.hashCode())");
		}
		for (final FieldMetadata field : locatedFields) {
			builder.append(".append(" + field.getFieldName() +")");
		}
		builder.append(".toHashCode();");

		bodyBuilder.appendFormalLine(builder.toString());

		return new MethodMetadataBuilder(getId(), Modifier.PUBLIC, HASH_CODE_METHOD_NAME, INT_PRIMITIVE, bodyBuilder);
	}

	@Override
	public String toString() {
		final ToStringCreator tsc = new ToStringCreator(this);
		tsc.append("identifier", getId());
		tsc.append("valid", valid);
		tsc.append("aspectName", aspectName);
		tsc.append("destinationType", destination);
		tsc.append("governor", governorPhysicalTypeMetadata.getId());
		tsc.append("itdTypeDetails", itdTypeDetails);
		return tsc.toString();
	}
}
