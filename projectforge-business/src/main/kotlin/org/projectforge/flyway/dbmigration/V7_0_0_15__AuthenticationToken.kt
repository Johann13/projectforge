/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.flyway.dbmigration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.SimpleJdbcInsert
import java.util.*

/**
 * EmployeeDO will now support timed annual leave days instead of fixed annual leave days (aka urlaubstage). So [org.projectforge.business.vacation.service.VacationStats]
 * may be calculated for former years properly if the amount of annual leave days was changed.
 */
class V7_0_0_15__AuthenticationToken : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val ds = context.configuration.dataSource
        log.info("Trying to migrate authentication token of t_pf_user.authentication_token.")
        val jdbc = JdbcTemplate(ds)
        val rs = jdbc.queryForRowSet("select u.pk as userId, u.authentication_token as token, u.stay_logged_in_key as stayLoggedInKey from t_pf_user as u")
        var counter = 0
        val now = Date()
        while (rs.next()) {
            val userId = rs.getInt("userId")
            val token = rs.getString("token")
            val stayLoggedInKey = rs.getString("stayLoggedInKey")
            if (token.isNullOrBlank() && stayLoggedInKey.isNullOrBlank()) {
                continue
            }
            ++counter
            var simpleJdbcInsert = SimpleJdbcInsert(ds).withTableName("T_PF_USER_AUTHENTICATIONS")
            val parameters = mutableMapOf<String, Any?>()
            parameters["pk"] = counter
            parameters["createdat"] = now
            parameters["createdby"] = "anon"
            parameters["modifiedat"] = now
            parameters["modifiedby"] = "anon"
            parameters["user_id"] = userId
            parameters["calendar_export_token"] = token
            parameters["stay_logged_in_key"] = stayLoggedInKey
            simpleJdbcInsert.execute(parameters)
        }
        if (counter > 0) { // counter > 0
            log.info("Number of successful migrated pf_user entries: $counter")
        } else {
            log.info("No pf_user entries found to migrate (OK for empty database or no user with authentication_token or stay_logged_in_key was found).")
        }
    }

    private val log = LoggerFactory.getLogger(V7_0_0_15__AuthenticationToken::class.java)
}