/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest.calendar.converter

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid
import org.apache.commons.lang3.StringUtils
import org.projectforge.rest.dto.CalEvent

class UidConverter : PropertyConverter() {
    override fun toVEvent(event: CalEvent): Property? {
        return if (event.uid != null) {
            Uid(event.uid)
        } else null

    }

    override fun fromVEvent(event: CalEvent, vEvent: VEvent): Boolean {
        if (vEvent.uid != null && !StringUtils.isEmpty(vEvent.uid.value)) {
            event.uid = vEvent.uid.value
            return true
        }

        return false
    }
}