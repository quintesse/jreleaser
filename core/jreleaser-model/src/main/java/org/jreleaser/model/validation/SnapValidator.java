/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2020-2022 The JReleaser authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jreleaser.model.validation;

import org.jreleaser.bundle.RB;
import org.jreleaser.model.Active;
import org.jreleaser.model.Artifact;
import org.jreleaser.model.Distribution;
import org.jreleaser.model.GitService;
import org.jreleaser.model.JReleaserContext;
import org.jreleaser.model.JReleaserModel;
import org.jreleaser.model.Snap;
import org.jreleaser.util.Errors;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jreleaser.model.validation.DistributionsValidator.validateArtifactPlatforms;
import static org.jreleaser.model.validation.ExtraPropertiesValidator.mergeExtraProperties;
import static org.jreleaser.model.validation.TemplateValidator.validateTemplate;
import static org.jreleaser.util.StringUtils.isBlank;

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
public abstract class SnapValidator extends Validator {
    public static void validateSnap(JReleaserContext context, Distribution distribution, Snap packager, Errors errors) {
        JReleaserModel model = context.getModel();
        Snap parentPackager = model.getPackagers().getSnap();

        if (!packager.isActiveSet() && parentPackager.isActiveSet()) {
            packager.setActive(parentPackager.getActive());
        }
        if (!packager.resolveEnabled(context.getModel().getProject(), distribution)) return;
        GitService service = model.getRelease().getGitService();
        if (!service.isReleaseSupported()) {
            packager.disable();
            return;
        }

        context.getLogger().debug("distribution.{}.snap", distribution.getName());

        List<Artifact> candidateArtifacts = packager.resolveCandidateArtifacts(context, distribution);
        if (candidateArtifacts.size() == 0) {
            packager.setActive(Active.NEVER);
            packager.disable();
            return;
        } else if (candidateArtifacts.size() > 1) {
            errors.configuration(RB.$("validation_packager_multiple_artifacts", "distribution." + distribution.getName() + ".snap"));
            packager.disable();
            return;
        }

        validateCommitAuthor(packager, parentPackager);
        Snap.SnapTap snap = packager.getSnap();
        snap.resolveEnabled(model.getProject());
        if (isBlank(snap.getName())) {
            snap.setName(distribution.getName() + "-snap");
        }
        validateTap(context, distribution, snap, parentPackager.getSnap(), "snap.snap");
        validateTemplate(context, distribution, packager, parentPackager, errors);
        mergeExtraProperties(packager, parentPackager);
        validateContinueOnError(packager, parentPackager);
        if (isBlank(packager.getDownloadUrl())) {
            packager.setDownloadUrl(parentPackager.getDownloadUrl());
        }
        mergeSnapPlugs(packager, parentPackager);
        mergeSnapSlots(packager, parentPackager);

        if (isBlank(packager.getPackageName())) {
            packager.setPackageName(parentPackager.getPackageName());
            if (isBlank(packager.getPackageName())) {
                packager.setPackageName(distribution.getName());
            }
        }
        if (isBlank(packager.getBase())) {
            packager.setBase(parentPackager.getBase());
            if (isBlank(packager.getBase())) {
                errors.configuration(RB.$("validation_must_not_be_blank", "distribution." + distribution.getName() + ".snap.base"));
            }
        }
        if (isBlank(packager.getGrade())) {
            packager.setGrade(parentPackager.getGrade());
            if (isBlank(packager.getGrade())) {
                errors.configuration(RB.$("validation_must_not_be_blank", "distribution." + distribution.getName() + ".snap.grade"));
            }
        }
        if (isBlank(packager.getConfinement())) {
            packager.setConfinement(parentPackager.getConfinement());
            if (isBlank(packager.getConfinement())) {
                errors.configuration(RB.$("validation_must_not_be_blank", "distribution." + distribution.getName() + ".snap.confinement"));
            }
        }
        if (!packager.isRemoteBuildSet() && parentPackager.isRemoteBuildSet()) {
            packager.setRemoteBuild(parentPackager.isRemoteBuild());
        }
        if (!packager.isRemoteBuild() && isBlank(packager.getExportedLogin())) {
            packager.setExportedLogin(parentPackager.getExportedLogin());
            if (isBlank(packager.getExportedLogin())) {
                errors.configuration(RB.$("validation_must_not_be_empty", "distribution." + distribution.getName() + ".snap.exportedLogin"));
            } else if (!context.getBasedir().resolve(packager.getExportedLogin()).toFile().exists()) {
                errors.configuration(RB.$("validation_directory_not_exist", "distribution." + distribution.getName() + ".snap.exportedLogin",
                    context.getBasedir().resolve(packager.getExportedLogin())));
            }
        }

        validateArtifactPlatforms(context, distribution, packager, candidateArtifacts, errors);

        packager.addArchitecture(parentPackager.getArchitectures());
        for (int i = 0; i < packager.getArchitectures().size(); i++) {
            Snap.Architecture arch = packager.getArchitectures().get(i);
            if (!arch.hasBuildOn()) {
                errors.configuration(RB.$("validation_snap_missing_buildon", "distribution." + distribution.getName() + ".snap.architectures", i));
            }
        }
    }

    private static void mergeSnapPlugs(Snap packager, Snap common) {
        Set<String> localPlugs = new LinkedHashSet<>();
        localPlugs.addAll(packager.getLocalPlugs());
        localPlugs.addAll(common.getLocalPlugs());
        packager.setLocalPlugs(localPlugs);

        Map<String, Snap.Plug> commonPlugs = common.getPlugs().stream()
            .collect(Collectors.toMap(Snap.Plug::getName, Snap.Plug::copyOf));
        Map<String, Snap.Plug> packagerPlugs = packager.getPlugs().stream()
            .collect(Collectors.toMap(Snap.Plug::getName, Snap.Plug::copyOf));
        commonPlugs.forEach((name, cp) -> {
            Snap.Plug tp = packagerPlugs.remove(name);
            if (null != tp) {
                cp.getAttributes().putAll(tp.getAttributes());
            }
        });
        commonPlugs.putAll(packagerPlugs);
        packager.setPlugs(new ArrayList<>(commonPlugs.values()));
    }

    private static void mergeSnapSlots(Snap packager, Snap common) {
        Set<String> localSlots = new LinkedHashSet<>();
        localSlots.addAll(packager.getLocalSlots());
        localSlots.addAll(common.getLocalSlots());
        packager.setLocalSlots(localSlots);

        Map<String, Snap.Slot> commonSlots = common.getSlots().stream()
            .collect(Collectors.toMap(Snap.Slot::getName, Snap.Slot::copyOf));
        Map<String, Snap.Slot> packagerSlots = packager.getSlots().stream()
            .collect(Collectors.toMap(Snap.Slot::getName, Snap.Slot::copyOf));
        commonSlots.forEach((name, cp) -> {
            Snap.Slot tp = packagerSlots.remove(name);
            if (null != tp) {
                cp.getAttributes().putAll(tp.getAttributes());
            }
        });
        commonSlots.putAll(packagerSlots);
        packager.setSlots(new ArrayList<>(commonSlots.values()));
    }
}
