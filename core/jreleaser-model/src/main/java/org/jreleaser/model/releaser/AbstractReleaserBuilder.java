/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2020-2021 Andres Almiray.
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
package org.jreleaser.model.releaser;

import org.jreleaser.model.Artifact;
import org.jreleaser.model.Distribution;
import org.jreleaser.model.JReleaserModel;
import org.jreleaser.util.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * @author Andres Almiray
 * @since 0.1.0
 */
public abstract class AbstractReleaserBuilder<R extends Releaser, B extends ReleaserBuilder<R, B>> implements ReleaserBuilder<R, B> {
    protected final List<Path> assets = new ArrayList<>();
    protected Logger logger;
    protected Path basedir;
    protected JReleaserModel model;

    protected final B self() {
        return (B) this;
    }

    @Override
    public B basedir(Path basedir) {
        this.basedir = requireNonNull(basedir, "'basedir' must not be null");
        return self();
    }

    @Override
    public B logger(Logger logger) {
        this.logger = requireNonNull(logger, "'logger' must not be null");
        return self();
    }

    @Override
    public B model(JReleaserModel model) {
        this.model = requireNonNull(model, "'model' must not be null");
        return self();
    }

    @Override
    public B addReleaseAsset(Path asset) {
        if (null != asset) {
            this.assets.add(asset);
        }
        return self();
    }

    @Override
    public B setReleaseAssets(List<Path> assets) {
        if (null != assets) {
            this.assets.addAll(assets);
        }
        return self();
    }

    protected void validate() {
        requireNonNull(basedir, "'basedir' must not be null");
        requireNonNull(logger, "'logger' must not be null");
        requireNonNull(model, "'model' must not be null");
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("'assets must not be empty");
        }
    }

    @Override
    public B configureWith(Path basedir, JReleaserModel model) {
        basedir(basedir);
        model(model);

        for (Distribution distribution : model.getDistributions().values()) {
            for (Artifact artifact : distribution.getArtifacts()) {
                addReleaseAsset(basedir.resolve(Paths.get(artifact.getPath())));
            }
        }

        return self();
    }
}