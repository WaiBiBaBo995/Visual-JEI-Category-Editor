package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.integration.*;

@LDLRegisterClient(name = "vjce_compat", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class CompatibilityScenario implements UIScenario {
    @Override public void define(ScenarioBuilder steps) {
        steps.waitUntil("JEI runtime ready", ctx -> VjceJeiPlugin.runtime != null)
                .step("damageable-stackable login regression", ctx -> CompatibilityAudit.run(ctx::check));
    }
}
