/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.bindings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.graylog2.database.ObjectIdSerializer;
import org.graylog2.shared.bindings.ObjectMapperModule;

public class ServerObjectMapperModule extends ObjectMapperModule {

    @Override
    protected ObjectMapper makeObjectMapper() {
        final ObjectMapper objectMapper = super.makeObjectMapper();
        
        objectMapper.registerModule(new SimpleModule().addSerializer(new ObjectIdSerializer()));

        return objectMapper;
    }
}
