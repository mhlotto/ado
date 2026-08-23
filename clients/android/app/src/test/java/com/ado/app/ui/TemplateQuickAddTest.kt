package com.ado.app.ui

import com.ado.app.data.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateQuickAddTest {
    @Test
    fun homeHidesOnlyTemplatesSupersededByGeneratedActions() {
        val visible = templatesForProjectQuickAdd(
            "home",
            listOf(
                template("summer_chores", "Summer chores"),
                template("fall_chores", "Fall chores"),
                template("winter_chores", "Winter chores"),
                template("spring_chores", "Spring chores"),
                template("leaving_house", "Leaving house"),
                template("market", "Market"),
                template("custom_summer", "Summer chores"),
            ),
        )

        assertEquals(listOf("market", "custom_summer"), visible.map { it.templateKey })
    }

    @Test
    fun dailyHidesDailyTemplateButKeepsUnrelatedTemplates() {
        val visible = templatesForProjectQuickAdd(
            "daily",
            listOf(template("daily", "Daily"), template("market", "Market")),
        )

        assertEquals(listOf("market"), visible.map { it.templateKey })
    }

    @Test
    fun ordinaryProjectsKeepEveryTemplate() {
        val templates = listOf(template("daily", "Daily"), template("market", "Market"))

        assertEquals(templates, templatesForProjectQuickAdd(null, templates))
        assertTrue(templatesForProjectQuickAdd("custom", templates).containsAll(templates))
    }

    private fun template(key: String, name: String) = Template(
        templateKey = key,
        name = name,
        projectCoreKey = null,
    )
}
