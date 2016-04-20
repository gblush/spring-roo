package org.springframework.roo.addon.web.mvc.controller.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.details.MethodMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationAttributeValue;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.classpath.details.annotations.EnumAttributeValue;
import org.springframework.roo.classpath.details.annotations.StringAttributeValue;
import org.springframework.roo.classpath.scanner.MemberDetails;
import org.springframework.roo.classpath.scanner.MemberDetailsScanner;
import org.springframework.roo.model.EnumDetails;
import org.springframework.roo.model.JavaSymbolName;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.SpringEnumDetails;
import org.springframework.roo.model.SpringJavaType;
import org.springframework.roo.support.logging.HandlerUtils;

/**
 * Implementation of {@link ControllerMVCService}.
 * 
 * @author Juan Carlos García
 * @since 2.0
 */
@Component
@Service
public class ControllerMVCServiceImpl implements ControllerMVCService {

  private static final Logger LOGGER = HandlerUtils.getLogger(ControllerMVCServiceImpl.class);

  // ------------ OSGi component attributes ----------------
  private BundleContext context;

  private TypeLocationService typeLocationService;
  private MemberDetailsScanner memberDetailsScanner;

  protected void activate(final ComponentContext context) {
    this.context = context.getBundleContext();
  }

  @Override
  public MethodMetadata getMVCMethodByRequestMapping(JavaType controller, EnumDetails method,
      String path, List<String> params, EnumDetails accept, EnumDetails consumes,
      EnumDetails produces, String headers) {

    // Getting controller member details
    MemberDetails controllerDetails =
        getMemberDetailsScanner().getMemberDetails(getClass().toString(),
            getTypeLocationService().getTypeDetails(controller));

    // Getting all controller methods
    List<MethodMetadata> methods = controllerDetails.getMethods();

    for (MethodMetadata definedMethod : methods) {

      // Getting request mapping annotation
      AnnotationMetadata requesMappingAnnotation =
          definedMethod.getAnnotation(SpringJavaType.REQUEST_MAPPING);

      if (requesMappingAnnotation != null) {
        // Get all attributes
        EnumDetails methodAttr =
            requesMappingAnnotation.getAttribute("method") == null ? null
                : (EnumDetails) requesMappingAnnotation.getAttribute("method").getValue();
        String valueAttr =
            requesMappingAnnotation.getAttribute("value") == null ? ""
                : (String) requesMappingAnnotation.getAttribute("value").getValue();

        // TODO: Get params and compare them

        EnumDetails acceptAttr =
            requesMappingAnnotation.getAttribute("accept") == null ? null
                : (EnumDetails) requesMappingAnnotation.getAttribute("accept").getValue();
        EnumDetails consumesAttr =
            requesMappingAnnotation.getAttribute("consumes") == null ? null
                : (EnumDetails) requesMappingAnnotation.getAttribute("consumes").getValue();
        EnumDetails producesAttr =
            requesMappingAnnotation.getAttribute("produces") == null ? null
                : (EnumDetails) requesMappingAnnotation.getAttribute("produces").getValue();
        String headersAttr =
            requesMappingAnnotation.getAttribute("headers") == null ? ""
                : (String) requesMappingAnnotation.getAttribute("headers").getValue();

        // If every attribute match, return this method
        if (methodAttr == method && valueAttr.equals(path) && acceptAttr == accept
            && consumesAttr == consumes && producesAttr == produces && headersAttr.equals(headers)) {
          return definedMethod;
        }
      }
    }

    return null;
  }

  @Override
  public AnnotationMetadataBuilder getRequestMappingAnnotation(EnumDetails method, String path,
      List<String> params, EnumDetails accept, EnumDetails consumes, EnumDetails produces,
      String headers) {

    List<AnnotationAttributeValue<?>> requestMappingAttributes =
        new ArrayList<AnnotationAttributeValue<?>>();

    // Adding method attribute. Force GET method if empty
    if (method != null) {
      requestMappingAttributes.add(new EnumAttributeValue(new JavaSymbolName("method"), method));
    } else {
      requestMappingAttributes.add(new EnumAttributeValue(new JavaSymbolName("method"),
          SpringEnumDetails.REQUEST_METHOD_GET));
    }

    // Adding path attribute
    if (StringUtils.isNotBlank(path)) {
      requestMappingAttributes.add(new StringAttributeValue(new JavaSymbolName("value"), path));
    }

    // TODO: Adding params attribute

    // Adding accept attribute
    if (accept != null) {
      requestMappingAttributes.add(new EnumAttributeValue(new JavaSymbolName("accept"), accept));
    }

    // Adding consumes attribute
    if (consumes != null) {
      requestMappingAttributes
          .add(new EnumAttributeValue(new JavaSymbolName("consumes"), consumes));
    }

    // Adding produces attribute
    if (produces != null) {
      requestMappingAttributes
          .add(new EnumAttributeValue(new JavaSymbolName("produces"), produces));
    }

    // Adding headers attribute
    if (StringUtils.isNotBlank(headers)) {
      requestMappingAttributes
          .add(new StringAttributeValue(new JavaSymbolName("headers"), headers));
    }

    return new AnnotationMetadataBuilder(SpringJavaType.REQUEST_MAPPING, requestMappingAttributes);
  }


  // Methods to obtain OSGi Services

  public TypeLocationService getTypeLocationService() {
    if (typeLocationService == null) {
      // Get all Services implement TypeLocationService interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(TypeLocationService.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          typeLocationService = (TypeLocationService) this.context.getService(ref);
          return typeLocationService;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load TypeLocationService on ControllerMVCServiceImpl.");
        return null;
      }
    } else {
      return typeLocationService;
    }
  }

  public MemberDetailsScanner getMemberDetailsScanner() {
    if (memberDetailsScanner == null) {
      // Get all Services implement MemberDetailsScanner interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(MemberDetailsScanner.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          memberDetailsScanner = (MemberDetailsScanner) this.context.getService(ref);
          return memberDetailsScanner;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load MemberDetailsScanner on ControllerMVCServiceImpl.");
        return null;
      }
    } else {
      return memberDetailsScanner;
    }
  }
}
