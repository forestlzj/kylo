/**
 * 
 */
package com.thinkbiganalytics.metadata.upgrade;

/*-
 * #%L
 * kylo-operational-metadata-upgrade-service
 * %%
 * Copyright (C) 2017 ThinkBig Analytics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.net.URL;

import com.thinkbiganalytics.KyloVersion;
import com.thinkbiganalytics.KyloVersionUtil;

/**
 *
 */
public interface UpgradeState { 
    
    KyloVersion getStartingVersion();

    void upgradeFrom(KyloVersion startingVersion);

    default URL getResource(String name) {
        String relName = name.startsWith("/") ? name.substring(1, name.length()) : name;
        return getClass().getResource(relName);
    }
    
    default KyloVersion asVersion(String major, String minor) {
        return new KyloVersionUtil.Version(major, minor);
    }

}
