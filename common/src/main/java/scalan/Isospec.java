package scalan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks annotated type as having support for isomorphic specialization. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Isospec {
}
