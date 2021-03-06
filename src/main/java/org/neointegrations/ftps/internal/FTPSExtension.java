package org.neointegrations.ftps.internal;

import org.mule.runtime.api.meta.Category;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;


/**
 * This is the main class of an extension, is the entry point from which configurations, connection providers, operations
 * and sources are going to be declared.
 */
@Xml(prefix = "ftps")
@Extension(name = "FTPS", vendor = "Neo Integrations", category = Category.CERTIFIED)
@Configurations(FTPSConfiguration.class)
public class FTPSExtension {

}
