package org.projectforge.rest.core

import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.PFDateTime
import org.projectforge.rest.converter.DateTimeFormat
import org.projectforge.ui.ResponseAction
import org.projectforge.ui.ValidationError
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import javax.servlet.http.HttpServletRequest


class RestHelper(
        /**
         * If not set, the user's time zone will be used (from [ThreadLocalUserContext]).
         */
        var timeZone: TimeZone? = null) {

    private val log = org.slf4j.LoggerFactory.getLogger(RestHelper::class.java)

    private val adapterMap = mutableMapOf<Class<*>, Any>()

    fun add(cls: Class<*>, typeAdapter: Any) {
        adapterMap.put(cls, typeAdapter)
    }

    fun <O : ExtendedBaseDO<Int>, B : BaseDao<O>, F : BaseSearchFilter>
            getList(dataObjectRest: AbstractStandardRest<O, B, F>, baseDao: BaseDao<O>, filter: F)
            : ResultSet<Any> {
        filter.isSortAndLimitMaxRowsWhileSelect = true
        val list = baseDao.getList(filter)
        val resultSet = ResultSet<Any>(dataObjectRest.filterList(list, filter), list.size)
        return resultSet
    }

    fun <O : ExtendedBaseDO<Int>, B : BaseDao<O>, F : BaseSearchFilter>
            saveOrUpdate(request: HttpServletRequest,
                         baseDao: BaseDao<O>, obj: O,
                         dataObjectRest: AbstractStandardRest<O, B, F>,
                         validationErrorsList: List<ValidationError>?)
            : ResponseEntity<ResponseAction> {
        if (validationErrorsList.isNullOrEmpty()) {
            val isNew = obj.id == null
            dataObjectRest.beforeSaveOrUpdate(request, obj)
            var id = baseDao.saveOrUpdate(obj) ?: obj.id
            dataObjectRest.afterSaveOrUpdate(obj)
            if (isNew) {
                obj.id = id as Int
                return ResponseEntity(dataObjectRest.afterSave(obj), HttpStatus.OK)
            } else {
                return ResponseEntity(dataObjectRest.afterUpdate(obj), HttpStatus.OK)
            }
        }
        // Validation error occurred:
        return ResponseEntity(ResponseAction(validationErrors = validationErrorsList), HttpStatus.NOT_ACCEPTABLE)
    }

    fun <O : ExtendedBaseDO<Int>, B : BaseDao<O>, F : BaseSearchFilter>
            undelete(baseDao: BaseDao<O>, obj: O,
                     dataObjectRest: AbstractStandardRest<O, B, F>,
                     validationErrorsList: List<ValidationError>?)
            : ResponseEntity<ResponseAction> {
        if (validationErrorsList.isNullOrEmpty()) {
            baseDao.undelete(obj)
            return ResponseEntity(dataObjectRest.afterUndelete(obj), HttpStatus.OK)
        }
        // Validation error occurred:
        return ResponseEntity(ResponseAction(validationErrors = validationErrorsList), HttpStatus.NOT_ACCEPTABLE)
    }

    fun <O : ExtendedBaseDO<Int>, B : BaseDao<O>, F : BaseSearchFilter>
            markAsDeleted(baseDao: BaseDao<O>, obj: O,
                          dataObjectRest: AbstractStandardRest<O, B, F>,
                          validationErrorsList: List<ValidationError>?)
            : ResponseEntity<ResponseAction> {
        if (validationErrorsList.isNullOrEmpty()) {
            baseDao.markAsDeleted(obj)
            return ResponseEntity(dataObjectRest.afterMarkAsDeleted(obj), HttpStatus.OK)
        }
        // Validation error occurred:
        return ResponseEntity(ResponseAction(validationErrors = validationErrorsList), HttpStatus.NOT_ACCEPTABLE)
    }

    fun <O : ExtendedBaseDO<Int>, B : BaseDao<O>, F : BaseSearchFilter>
            delete(baseDao: BaseDao<O>, obj: O,
                   dataObjectRest: AbstractStandardRest<O, B, F>,
                   validationErrorsList: List<ValidationError>?)
            : ResponseEntity<ResponseAction> {
        if (validationErrorsList.isNullOrEmpty()) {
            baseDao.delete(obj)
            return ResponseEntity(dataObjectRest.afterDelete(obj), HttpStatus.OK)
        }
        // Validation error occurred:
        return ResponseEntity(ResponseAction(validationErrors = validationErrorsList), HttpStatus.NOT_ACCEPTABLE)
    }

    fun buildUri(request: HttpServletRequest, path: String): URI {
        return URI("${getRootUrl(request)}/$path")
    }

    fun getRootUrl(request: HttpServletRequest): String {
        val serverName = request.serverName
        val portNumber = request.serverPort
        return if (portNumber != 80 && portNumber != 443) "$serverName:$portNumber" else serverName
    }

    fun parseJSDateTime(jsString: String?): PFDateTime? {
        if (jsString.isNullOrBlank())
            return null
        try {
            val length = jsString.length
            val formatter =
                    when {
                        length > 19 -> jsonDateTimeFormatter
                        length > 16 -> jsonDateTimeSecondsFormatter
                        length > 10 -> jsonDateTimeMinutesFormatter
                        else -> jsonDateFormatter
                    }
            if (formatter != jsonDateFormatter)
                return PFDateTime.parseUTCDate(jsString, formatter)
            val local = LocalDate.parse(jsString, jsonDateFormatter) // Parses UTC as local date.
            return PFDateTime.from(local)
        } catch (ex: DateTimeParseException) {
            log.error("Error while parsing date '$jsString': ${ex.message}.")
            return null
        }
    }

    fun parseLong(request: HttpServletRequest, parameter: String): Long? {
        val value = request.getParameter(parameter)
        if (value == null)
            return null
        try {
            return value.toLong()
        } catch (ex: DateTimeParseException) {
            log.error("Error while parsing date millis '$value': ${ex.message}.")
            return null
        }
    }

    companion object {
        private val jsonDateTimeFormatter = DateTimeFormatter.ofPattern(DateTimeFormat.JS_DATE_TIME_MILLIS.pattern)
        private val jsonDateTimeSecondsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val jsonDateTimeMinutesFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        private val jsonDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
