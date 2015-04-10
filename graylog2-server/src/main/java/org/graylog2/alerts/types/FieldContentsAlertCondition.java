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

import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.graylog2.alerts.AbstractAlertCondition;
import org.graylog2.indexer.InvalidRangeFormatException;
import org.graylog2.indexer.results.FieldStatsResult;
import org.graylog2.indexer.results.ResultMessage;
import org.graylog2.indexer.searches.Searches;
import org.graylog2.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.MessageSummary;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.streams.Stream;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FieldContentsAlertCondition extends AbstractAlertCondition {
    private static final Logger LOG = LoggerFactory.getLogger(FieldContentsAlertCondition.class);

    public enum CheckType {
        COMPARE, COMPARE_IGNORE_CASE, MATCH, MATCH_IGNORE_CASE, STDDEV
    }

    public enum MatchType {
        MATCH, NOT_MATCH
    }

    public interface Factory {
        FieldContentsAlertCondition createAlertCondition(Stream stream, String id, DateTime createdAt, @Assisted("userid") String creatorUserId, Map<String, Object> parameters);
    }

    private final int time;
    private final MatchType matchType;
    private final Number match;
    private final CheckType type;
    private final String field;
    private final DecimalFormat decimalFormat;
    private final Searches searches;
    private List<Message> searchHits = Collections.emptyList();

    @AssistedInject
    public FieldContentsAlertCondition(Searches searches, @Assisted Stream stream, @Nullable @Assisted String id, @Assisted DateTime createdAt, @Assisted("userid") String creatorUserId, @Assisted Map<String, Object> parameters) {
        super(stream, id, Type.FIELD_CONTENTS, createdAt, creatorUserId, parameters);
        this.searches = searches;

        this.decimalFormat = new DecimalFormat("#.###");

        this.time = (Integer) parameters.get("time");

        // specifies the type of comparison to perform
        this.matchType = MatchType.valueOf(((String) parameters.get("match_type")).toUpperCase());

        // this is the string to compare against
        this.match = (Number) parameters.get("match");

        // specifies if a match or a no-match will trigger an alert
        this.type = CheckType.valueOf(((String) parameters.get("type")).toUpperCase());

        // specifies the field to compare
        this.field = (String) parameters.get("field");
    }

    @Override
    public String getDescription() {
        return "time: " + time
                + ", field: " + field
                + ", check type: " + type.toString().toLowerCase()
                + ", match_type: " + matchType.toString().toLowerCase()
                + ", match: " + decimalFormat.format(match)
                + ", grace: " + grace;
    }

    @Override
    protected CheckResult runCheck() {
        this.searchHits = Collections.emptyList();
        final List<MessageSummary> summaries = Lists.newArrayList();
        try {
            String filter = "streams:" + stream.getId();
            // TODO DFCH try to get the real message instead of only the
            FieldStatsResult fieldStatsResult = searches.fieldStats(field, "*", filter, new RelativeRange(time * 60));
            if (getBacklog() != null && getBacklog() > 0) {
                this.searchHits = Lists.newArrayList();
                for (ResultMessage resultMessage : fieldStatsResult.getSearchHits()) {
                    final Message msg = new Message(resultMessage.getMessage());
                    this.searchHits.add(msg);
                    summaries.add(new MessageSummary(resultMessage.getIndex(), msg));
                }
            }

            if (fieldStatsResult.getCount() == 0) {
                LOG.debug("Alert check <{}> did not match any messages. Returning not triggered.", type);
                return new CheckResult(false);
            }

            double result;
            switch (type) {
                case COMPARE:
                    // TODO DFCH change the query FieldStatsReturn
                    result = fieldStatsResult.getMean();
                    // fieldStatsResult.getSearchHits().
                    break;
                case COMPARE_IGNORE_CASE:
                    // TODO DFCH change the query FieldStatsReturn
                    result = fieldStatsResult.getMin();
                    break;
                case MATCH:
                    // TODO DFCH change the query FieldStatsReturn
                    result = fieldStatsResult.getMax();
                    break;
                case MATCH_IGNORE_CASE:
                    // TODO DFCH change the query FieldStatsReturn
                    result = fieldStatsResult.getSum();
                    break;
                case STDDEV:
                    // TODO DFCH change the query FieldStatsReturn
                    result = fieldStatsResult.getStdDeviation();
                    break;
                default:
                    LOG.error("No such field value check type: [{}]. Returning not triggered.", type);
                    return new CheckResult(false);
            }

            LOG.debug("Alert check <{}> result: [{}]", id, result);

            if (Double.isInfinite(result)) {
                // This happens when there are no ES results/docs.
                LOG.debug("Infinite value. Returning not triggered.");
                return new CheckResult(false);
            }

            boolean triggered = false;
            switch (matchType) {
                case MATCH:
                    triggered = result > match.doubleValue();
                    break;
                case NOT_MATCH:
                    triggered = result < match.doubleValue();
                    break;
            }

            if (triggered) {
                final String resultDescription = "Field " + field + " had a " + type + " of "
                        + decimalFormat.format(result) + " in the last " + time + " minutes with trigger condition "
                        + matchType + " than " + decimalFormat.format(match) + ". "
                        + "(Current grace time: " + grace + " minutes)";
                return new CheckResult(true, this, resultDescription, Tools.iso8601(), summaries);
            } else {
                return new CheckResult(false);
            }
        } catch (InvalidRangeParametersException e) {
            // cannot happen lol
            LOG.error("Invalid timerange.", e);
            return null;
        } catch (InvalidRangeFormatException e) {
            // lol same here
            LOG.error("Invalid timerange format.", e);
            return null;
        } catch (Searches.FieldTypeException e) {
            LOG.debug("Field [{}] seems not to have a numerical type or doesn't even exist at all. Returning not triggered.", field, e);
            return new CheckResult(false);
        }
    }

    @Override
    public List<Message> getSearchHits() {
        return this.searchHits;
    }
}
