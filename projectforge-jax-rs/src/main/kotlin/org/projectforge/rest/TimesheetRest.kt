package org.projectforge.rest

import org.projectforge.business.timesheet.TimesheetDO
import org.projectforge.business.timesheet.TimesheetDao
import org.projectforge.business.timesheet.TimesheetFilter
import org.projectforge.rest.core.AbstractStandardRest
import org.projectforge.ui.*
import org.springframework.stereotype.Component
import javax.ws.rs.Path

@Component
@Path("timesheet")
class TimesheetRest() : AbstractStandardRest<TimesheetDO, TimesheetDao, TimesheetFilter>(TimesheetDao::class.java, TimesheetFilter::class.java, "timesheet.title") {
    /**
     * Initializes new timesheets for adding.
     */
    override fun newBaseDO(): TimesheetDO {
        val sheet = super.newBaseDO()
        return sheet
    }

    override fun validate(validationErrors: MutableList<ValidationError>, obj: TimesheetDO) {
    }

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.UIResultSetTable() // Todo customer, project, KW, WT, Range, Duration
                        .add(lc, "user", "task")
                        .add(UITableColumn("kost2.kunde", "fibu.kunde", formatter = Formatter.CUSTOMER))
                        .add(UITableColumn("kost2.projekt", "fibu.projekt", formatter = Formatter.PROJECT))
                        .add(UITableColumn("kost2", "fibu.kost2", formatter = Formatter.COST2))
                        .add(lc,  "location", "description"))
        layout.getTableColumnById("user").formatter = Formatter.USER
        layout.getTableColumnById("task").formatter = Formatter.TASK_PATH
        LayoutUtils.addListFilterContainer(layout, "longFormat", "recursive",
                filterClass = TimesheetFilter::class.java)
        return LayoutUtils.processListPage(layout)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dataObject: TimesheetDO?): UILayout {
        val layout = super.createEditLayout(dataObject)
                .add(lc, "task", "kost2", "user", "startTime", "stopTime")
                .add(UICustomized("taskConsumption"))
                .add(lc, "location", "description")
        return LayoutUtils.processEditPage(layout, dataObject)
    }
}
