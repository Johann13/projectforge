/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2014 Kai Reinhard (k.reinhard@micromata.de)
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

package org.projectforge.rest.converter;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;
import org.projectforge.rest.ConnectionSettings;

import java.lang.reflect.Type;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Serialization and deserialization for dates in ISO format and UTC time-zone.
 * @author Kai Reinhard (k.reinhard@micromata.de)
 *
 */
public class DateTypeAdapter implements JsonSerializer<Date>, JsonDeserializer<Date>
{
  private final DateFormat dateFormatter;

  public DateTypeAdapter()
  {
    final ConnectionSettings settings = ConnectionSettings.get();
    dateFormatter = new SimpleDateFormat("yyyy-MM-dd", settings.getLocale());
  }

  @Override
  public synchronized JsonElement serialize(final Date date, final Type type, final JsonSerializationContext jsonSerializationContext)
  {
    synchronized (dateFormatter) {
      final String dateFormatAsString = dateFormatter.format(date);
      return new JsonPrimitive(dateFormatAsString);
    }
  }

  @Override
  public synchronized Date deserialize(final JsonElement jsonElement, final Type type,
      final JsonDeserializationContext jsonDeserializationContext)
  {
    try {
      synchronized (dateFormatter) {
        final String element = jsonElement.getAsString();
        if (element == null) {
          return null;
        }
        if (StringUtils.isNumeric(element) == true) {
          final Date date = new Date(Long.parseLong(element));
          return date;
        }
        final java.util.Date date = dateFormatter.parse(element);
        return new Date(date.getTime());
      }
    } catch (final ParseException e) {
      throw new JsonSyntaxException(jsonElement.getAsString(), e);
    }
  }
}
