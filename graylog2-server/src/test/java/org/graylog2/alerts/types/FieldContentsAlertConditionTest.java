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
package org.graylog2.alerts.types;

import org.graylog2.alerts.AlertConditionTest;
import org.graylog2.indexer.InvalidRangeFormatException;
import org.graylog2.indexer.results.FieldStatsResult;
import org.graylog2.indexer.searches.Searches;
import org.graylog2.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.AlertCondition;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Matchers;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Ignore
public class FieldContentsAlertConditionTest extends AlertConditionTest {

    // TODO DFCH remove later on - check if tests are really being executed
    @Test
    public void testWillFail()
    {
        assertEquals(true, false);
    }
    @Test
    public void testConstructor() throws Exception {
        Map<String, Object> parameters = getParametersMap(0,
                0,
                FieldContentsAlertCondition.MatchType.NOT_MATCH,
                FieldContentsAlertCondition.CheckType.MATCH,
                0,
                "response_time");

        final FieldContentsAlertCondition fieldContentsAlertCondition = getTestInstance(FieldContentsAlertCondition.class, parameters);

        assertNotNull(fieldContentsAlertCondition);
        assertNotNull(fieldContentsAlertCondition.getDescription());
    }

    @Test
    public void testRunCheckHigherPositive() throws Exception {
        for (FieldContentsAlertCondition.CheckType checkType : FieldContentsAlertCondition.CheckType.values()) {
            final double threshold = 50.0;
            final double higherThanThreshold = threshold + 10;
            final FieldContentsAlertCondition fieldContentsAlertCondition = getTestInstance(FieldContentsAlertCondition.class,
                    getParametersMap(0, 0, FieldContentsAlertCondition.MatchType.NOT_MATCH, checkType, threshold, "response_time"));

            fieldStatsShouldReturn(getFieldStatsResult(checkType, higherThanThreshold));
            alertLastTriggered(-1);

            AlertCondition.CheckResult result = alertService.triggered(fieldContentsAlertCondition);

            assertTriggered(fieldContentsAlertCondition, result);
        }
    }

    @Test
    public void testRunCheckHigherNegative() throws Exception {
        for (FieldContentsAlertCondition.CheckType checkType : FieldContentsAlertCondition.CheckType.values()) {
            final double threshold = 50.0;
            final double lowerThanThreshold = threshold - 10;
            FieldContentsAlertCondition fieldContentsAlertCondition = getFieldContentsAlertCondition(getParametersMap(0, 0,
                    FieldContentsAlertCondition.MatchType.NOT_MATCH,
                    checkType, threshold, "response_time"));

            fieldStatsShouldReturn(getFieldStatsResult(checkType, lowerThanThreshold));
            alertLastTriggered(-1);

            AlertCondition.CheckResult result = alertService.triggered(fieldContentsAlertCondition);

            assertNotTriggered(result);
        }
    }

    @Test
    public void testRunCheckLowerPositive() throws Exception {
        for (FieldContentsAlertCondition.CheckType checkType : FieldContentsAlertCondition.CheckType.values()) {
            final double threshold = 50.0;
            final double lowerThanThreshold = threshold - 10;
            FieldContentsAlertCondition fieldContentsAlertCondition = getFieldContentsAlertCondition(getParametersMap(0, 0,
                    FieldContentsAlertCondition.MatchType.MATCH,
                    checkType, threshold, "response_time"));

            fieldStatsShouldReturn(getFieldStatsResult(checkType, lowerThanThreshold));
            alertLastTriggered(-1);

            AlertCondition.CheckResult result = alertService.triggered(fieldContentsAlertCondition);

            assertTriggered(fieldContentsAlertCondition, result);
        }
    }

    @Test
    public void testRunCheckLowerNegative() throws Exception {
        for (FieldContentsAlertCondition.CheckType checkType : FieldContentsAlertCondition.CheckType.values()) {
            final double threshold = 50.0;
            final double higherThanThreshold = threshold + 10;
            FieldContentsAlertCondition fieldContentsAlertCondition = getFieldContentsAlertCondition(getParametersMap(0, 0,
                    FieldContentsAlertCondition.MatchType.MATCH,
                    checkType, threshold, "response_time"));

            fieldStatsShouldReturn(getFieldStatsResult(checkType, higherThanThreshold));
            alertLastTriggered(-1);

            AlertCondition.CheckResult result = alertService.triggered(fieldContentsAlertCondition);

            assertNotTriggered(result);
        }
    }

    protected Map<String, Object> getParametersMap(Integer grace,
                                                   Integer time,
                                                   FieldContentsAlertCondition.MatchType match_type,
                                                   FieldContentsAlertCondition.CheckType check_type,
                                                   Number threshold,
                                                   String field) {
        Map<String, Object> parameters = super.getParametersMap(grace, time, threshold);
        parameters.put("match_type", match_type.toString());
        parameters.put("field", field);
        parameters.put("type", check_type.toString());

        return parameters;
    }

    protected FieldContentsAlertCondition getFieldContentsAlertCondition(Map<String, Object> parameters) {
        return new FieldContentsAlertCondition(
                searches,
                stream,
                CONDITION_ID,
                Tools.iso8601(),
                STREAM_CREATOR,
                parameters);
    }

    protected void fieldStatsShouldReturn(FieldStatsResult fieldStatsResult) {
        try {
            when(searches.fieldStats(anyString(), Matchers.eq("*"), anyString(), any(RelativeRange.class))).thenReturn(fieldStatsResult);
        } catch (InvalidRangeFormatException | Searches.FieldTypeException e) {
            assertNotNull("This should not return an exception!", e);
        }
    }

    protected FieldStatsResult getFieldStatsResult(FieldContentsAlertCondition.CheckType type, Number retValue) {
        final Double value = (Double) retValue;
        final FieldStatsResult fieldStatsResult = mock(FieldStatsResult.class);

        when(fieldStatsResult.getCount()).thenReturn(1L);

        switch (type) {
            case COMPARE_IGNORE_CASE:
                when(fieldStatsResult.getMin()).thenReturn(value);
            case MATCH:
                when(fieldStatsResult.getMax()).thenReturn(value);
            case COMPARE:
                when(fieldStatsResult.getMean()).thenReturn(value);
            case MATCH_IGNORE_CASE:
                when(fieldStatsResult.getSum()).thenReturn(value);
            case STDDEV:
                when(fieldStatsResult.getStdDeviation()).thenReturn(value);
        }
        return fieldStatsResult;
    }
}