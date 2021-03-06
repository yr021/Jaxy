
package com.rac021.jaxy.api.qualifiers ;

import java.lang.annotation.Target ;
import java.lang.annotation.Retention ;
import java.lang.annotation.ElementType ;
import java.lang.annotation.RetentionPolicy ;

/**
 *
 * @author ryahiaoui
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RootDto {
}