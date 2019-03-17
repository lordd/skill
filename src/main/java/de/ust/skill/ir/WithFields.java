package de.ust.skill.ir;

import java.util.List;

public interface WithFields extends ReferenceType {

    /**
     * @return the fields added in this type
     */
    public abstract List<Field> getFields();

    /**
     * @return the fields in this type and all super types
     */
    public abstract List<Field> getAllFields();

    /**
     * @return views of this entity
     */
    public abstract List<View> getViews();

    /**
     * @return language customizations of this entity
     */
    public abstract List<LanguageCustomization> getCustomizations();
}
