package com.structurizr;

import com.structurizr.annotation.ContainerDependency;
import com.structurizr.annotation.SoftwareSystemDependency;
import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.SoftwareSystem;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import org.reflections.Reflections;
import org.reflections.scanners.FieldAnnotationsScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.AnnotatedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpringComponentFinder {

    private Container container;
    private String packageToScan;

    private Map<String,Component> componentsByType = new HashMap<>();

    private Reflections reflections;

    public SpringComponentFinder(Container container, String packageToScan) {
        this.container = container;
        this.packageToScan = packageToScan;

        this.reflections = new Reflections(new ConfigurationBuilder()
                  .filterInputsBy(new FilterBuilder().includePackage(packageToScan))
                  .setUrls(ClasspathHelper.forPackage(packageToScan))
                  .setScanners(new TypeAnnotationsScanner(), new SubTypesScanner(), new FieldAnnotationsScanner()));
    }

    public void findComponents() throws Exception {
        findComponentsForClass(org.springframework.stereotype.Controller.class);
        findComponentsForInterface(org.springframework.stereotype.Service.class);
        findComponentsForInterface(org.springframework.stereotype.Repository.class);
        findComponentsForInterface(org.springframework.stereotype.Component.class);
    }

    private void findComponentsForClass(Class type) {
        Set<Class<?>> componentTypes = reflections.getTypesAnnotatedWith(type);
        for (Class<?> componentType : componentTypes) {
            Component component = container.addComponentWithType(componentType.getCanonicalName(), "");
            component.setTechnology("Spring " + type.getSimpleName());
            componentsByType.put(component.getFullyQualifiedClassName(), component);
        }
    }

    private void findComponentsForInterface(Class type) {
        Set<Class<?>> componentTypes = reflections.getTypesAnnotatedWith(type);
        for (Class<?> componentType : componentTypes) {
            Component component = container.addComponentWithType(componentType.getInterfaces()[0].getCanonicalName(), "");
            component.setTechnology("Spring " + type.getSimpleName());
            componentsByType.put(component.getFullyQualifiedClassName(), component);
        }
    }

    public void findComponentDependencies() throws Exception {
        for (Component component : componentsByType.values()) {
            // find dependencies of the component type itself
            addEfferentDependencies(component, component.getFullyQualifiedClassName(), 1);

            // and also find the implementations of the component (i.e. an interface was marked as a component)
            Set<String> componentImplementations = reflections.getStore().getSubTypesOf(component.getFullyQualifiedClassName());
            for (String componentImplementation : componentImplementations) {
                addEfferentDependencies(component, componentImplementation, 1);
            }
        }
    }

    private void addEfferentDependencies(Component component, String implementationType, int depth) {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass cc = pool.get(implementationType);
            for (Object referencedType : cc.getRefClasses()) {
                String referencedTypeName = (String)referencedType;
                if (referencedTypeName.startsWith(packageToScan)) {

                    if (componentsByType.containsKey(referencedTypeName)) {
                        Component destinationComponent = componentsByType.get(referencedTypeName);
                        if (component != destinationComponent) {
                            component.uses(destinationComponent, "");
                        }
                    } else if (!referencedTypeName.equals(implementationType) && depth < 10) {
                        addEfferentDependencies(component, referencedTypeName, ++depth);
                    }
                }
            }
        } catch (NotFoundException nfe) {
            nfe.printStackTrace();
        }
    }

    public void findSoftwareSystemDependencies() {
        Set<Class<?>> componentImplementationTypes = reflections.getTypesAnnotatedWith(SoftwareSystemDependency.class);
        for (Class<?> componentImplementationType : componentImplementationTypes) {

            Component component = findComponentFor(componentImplementationType);
            if (component != null) {
                // find the software system with a given name
                String target = componentImplementationType.getAnnotation(SoftwareSystemDependency.class).target();
                String description = componentImplementationType.getAnnotation(SoftwareSystemDependency.class).description();
                SoftwareSystem targetSoftwareSystem = component.getModel().getSoftwareSystemWithName(target);
                if (targetSoftwareSystem != null) {
                    component.uses(targetSoftwareSystem, description);
                }
            }
        }
    }

    public void findContainerDependencies() {
        Set<Class<?>> componentImplementationTypes = reflections.getTypesAnnotatedWith(ContainerDependency.class);
        for (Class<?> componentImplementationType : componentImplementationTypes) {

            Component component = findComponentFor(componentImplementationType);
            if (component != null) {
                // find the software system with a given name
                String target = componentImplementationType.getAnnotation(ContainerDependency.class).target();
                String description = componentImplementationType.getAnnotation(ContainerDependency.class).description();
                Container targetContainer = component.getParent().getParent().getContainerWithName(target);
                if (targetContainer != null) {
                    component.uses(targetContainer, description);
                }
            }
        }
    }

    private Component findComponentFor(Class<?> componentImplementationType) {
        AnnotatedType[] annotatedTypes = componentImplementationType.getAnnotatedInterfaces();
        for (AnnotatedType annotatedType : annotatedTypes) {
            if (componentsByType.containsKey(annotatedType.getType().getTypeName())) {
                return componentsByType.get(annotatedType.getType().getTypeName());
            }
        }

        return null;
    }

}