package ru.redenergy.resolver.domain;

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

public class ResolveDSLParser {

    private GroovyShell shell = constructShell();

    private GroovyShell constructShell(){
        CompilerConfiguration config = new CompilerConfiguration();
        ImportCustomizer importCustomizer = new ImportCustomizer();
        importCustomizer.addImports(Repository.class.getName(), Dependencies.class.getName(), Dependencies.Artifact.class.getName());
        config.setScriptBaseClass(ResolveBaseDslScript.class.getName());
        return new GroovyShell(config);
    }

    public GroovyShell shell(){
        return shell;
    }
}
