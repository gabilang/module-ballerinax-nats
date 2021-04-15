/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.stdlib.nats.plugin;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.stdlib.nats.plugin.PluginConstants.CompilationErrors;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import java.util.List;
import java.util.Optional;

/**
 * NATS service compilation analysis task.
 */
public class NatsServiceAnalysisTask implements AnalysisTask<SyntaxNodeAnalysisContext> {
    @Override
    public void perform(SyntaxNodeAnalysisContext context) {
        if (!isNatsService(context)) {
            return;
        }
        new NatsServiceValidator(context).validate();
    }

    private boolean isNatsService(SyntaxNodeAnalysisContext context) {
        boolean isNatsService = false;
        SemanticModel semanticModel = context.semanticModel();
        ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) context.node();
        Optional<Symbol> symbol = semanticModel.symbol(serviceDeclarationNode);
        if (symbol.isPresent()) {
            ServiceDeclarationSymbol serviceDeclarationSymbol = (ServiceDeclarationSymbol) symbol.get();
            List<TypeSymbol> listeners = serviceDeclarationSymbol.listenerTypes();
            if (listeners.size() > 1) {
                context.reportDiagnostic(PluginUtils.getDiagnostic(CompilationErrors.INVALID_MULTIPLE_LISTENERS,
                        DiagnosticSeverity.ERROR, serviceDeclarationNode.location()));
            } else {
                if (listeners.get(0).typeKind() == TypeDescKind.UNION) {
                    UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) listeners.get(0);
                    List<TypeSymbol> members = unionTypeSymbol.memberTypeDescriptors();
                    for (TypeSymbol memberSymbol : members) {
                        Optional<ModuleSymbol> module = memberSymbol.getModule();
                        if (module.isPresent()) {
                            isNatsService = validateModuleId(module.get());
                        }
                    }
                } else {
                    Optional<ModuleSymbol> module = listeners.get(0).getModule();
                    if (module.isPresent()) {
                        isNatsService = validateModuleId(module.get());
                    }
                }
            }
        }
        return isNatsService;
    }

    private boolean validateModuleId(ModuleSymbol moduleSymbol) {
        String moduleName = moduleSymbol.id().moduleName();
        String orgName = moduleSymbol.id().orgName();
        return moduleName.equals(PluginConstants.PACKAGE_PREFIX) &&
                orgName.equals(PluginConstants.PACKAGE_ORG);
    }
}
