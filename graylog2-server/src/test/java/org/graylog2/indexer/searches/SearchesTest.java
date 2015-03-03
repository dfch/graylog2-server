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
package org.graylog2.indexer.searches;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.test.ElasticsearchSingleNodeTest;
import org.graylog2.Configuration;
import org.graylog2.configuration.ElasticsearchConfiguration;
import org.graylog2.indexer.Deflector;
import org.graylog2.indexer.indices.Indices;
import org.graylog2.indexer.messages.Messages;
import org.graylog2.indexer.ranges.IndexRange;
import org.graylog2.indexer.ranges.IndexRangeService;
import org.graylog2.indexer.results.CountResult;
import org.graylog2.indexer.results.FieldStatsResult;
import org.graylog2.indexer.results.HistogramResult;
import org.graylog2.indexer.results.TermsResult;
import org.graylog2.indexer.results.TermsStatsResult;
import org.graylog2.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.database.validators.Validator;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SearchesTest extends ElasticsearchSingleNodeTest {
    private static final String INDEX_NAME = "graylog";
    private static final List<IndexRange> INDEX_RANGES = Collections.<IndexRange>singletonList(new IndexRange() {
        @Override
        public String getIndexName() {
            return INDEX_NAME;
        }

        @Override
        public DateTime getCalculatedAt() {
            return DateTime.now();
        }

        @Override
        public DateTime getStart() {
            return new DateTime(2015, 1, 1, 0, 0, DateTimeZone.UTC);
        }

        @Override
        public int getCalculationTookMs() {
            return 0;
        }

        @Override
        public String getId() {
            return "id";
        }

        @Override
        public Map<String, Object> getFields() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Validator> getValidations() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Validator> getEmbeddedValidations(String key) {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Object> asMap() {
            return Collections.emptyMap();
        }
    });

    private final Deflector deflector = mock(Deflector.class);
    private final IndexRangeService indexRangeService = mock(IndexRangeService.class);
    private final Indices indices = new Indices(node(), new ElasticsearchConfiguration());

    private Searches searches;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(indices.create(INDEX_NAME));

        byte[] bulkData = Resources.toByteArray(
                SearchesTest.class.getResource("/org/graylog2/indexer/searches/SearchesTest.txt")
        );
        BulkResponse bulkResponse = client().bulk(
                client().prepareBulk()
                        .add(bulkData, 0, bulkData.length, false, INDEX_NAME, Messages.TYPE)
                        .setRefresh(true)
                        .request())
                .get();
        assumeFalse(bulkResponse.hasFailures());

        initMocks(this);
        when(indexRangeService.getFrom(anyInt())).thenReturn(INDEX_RANGES);
        searches = new Searches(new Configuration(), deflector, indexRangeService, node());
    }

    @Test
    public void testCount() throws Exception {
        CountResult result = searches.count("*", new AbsoluteRange(
                new DateTime(2015, 1, 1, 0, 0),
                new DateTime(2015, 1, 2, 0, 0)));

        Assertions.assertThat(result.getCount()).isEqualTo(10L);
    }

    @Test
    public void testTerms() throws Exception {
        TermsResult result = searches.terms("n", 25, "*", new AbsoluteRange(
                new DateTime(2015, 1, 1, 0, 0),
                new DateTime(2015, 1, 2, 0, 0)));

        Assertions.assertThat(result.getTotal()).isEqualTo(10L);
        Assertions.assertThat(result.getMissing()).isEqualTo(2L);
        Assertions.assertThat(result.getTerms())
                .hasSize(4)
                .containsEntry("1", 2L)
                .containsEntry("2", 2L)
                .containsEntry("3", 3L)
                .containsEntry("4", 1L);

    }

    @Test
    public void testTermsStats() throws Exception {
        TermsStatsResult r = searches.termsStats("message", "n", Searches.TermsStatsOrder.COUNT, 25, "*",
                new AbsoluteRange(
                        new DateTime(2015, 1, 1, 0, 0),
                        new DateTime(2015, 1, 2, 0, 0))
        );

        Assertions.assertThat(r.getResults()).hasSize(2);
        Assertions.assertThat((Map<String, Object>) r.getResults().get(0))
                .hasSize(7)
                .containsEntry("key_field", "ho");
    }

    @Test
    public void testFieldStats() throws Exception {
        FieldStatsResult result = searches.fieldStats("n", "*", new AbsoluteRange(
                new DateTime(2015, 1, 1, 0, 0),
                new DateTime(2015, 1, 2, 0, 0)));

        Assertions.assertThat(result.getSearchHits()).hasSize(10);
        Assertions.assertThat(result.getCount()).isEqualTo(8);
        Assertions.assertThat(result.getMin()).isEqualTo(1.0);
        Assertions.assertThat(result.getMax()).isEqualTo(4.0);
        Assertions.assertThat(result.getMean()).isEqualTo(2.375);
        Assertions.assertThat(result.getSum()).isEqualTo(19.0);
        Assertions.assertThat(result.getSumOfSquares()).isEqualTo(53.0);
        Assertions.assertThat(result.getVariance()).isEqualTo(0.984375);
        Assertions.assertThat(result.getStdDeviation()).isEqualTo(0.9921567416492215);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHistogram() throws Exception {
        final AbsoluteRange range = new AbsoluteRange(new DateTime(2015, 1, 1, 0, 0), new DateTime(2015, 1, 2, 0, 0));
        HistogramResult h = searches.histogram("*", Searches.DateHistogramInterval.MINUTE, range);

        Assertions.assertThat(h.getInterval()).isEqualTo(Searches.DateHistogramInterval.MINUTE);
        Assertions.assertThat(h.getHistogramBoundaries()).isEqualTo(range);
        Assertions.assertThat(h.getResults())
                .hasSize(5)
                .containsEntry(new DateTime(2015, 1, 1, 0, 1, DateTimeZone.UTC).getMillis() / 1000L, 2L)
                .containsEntry(new DateTime(2015, 1, 1, 0, 2, DateTimeZone.UTC).getMillis() / 1000L, 2L)
                .containsEntry(new DateTime(2015, 1, 1, 0, 3, DateTimeZone.UTC).getMillis() / 1000L, 2L)
                .containsEntry(new DateTime(2015, 1, 1, 0, 4, DateTimeZone.UTC).getMillis() / 1000L, 2L)
                .containsEntry(new DateTime(2015, 1, 1, 0, 5, DateTimeZone.UTC).getMillis() / 1000L, 2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFieldHistogram() throws Exception {
        final AbsoluteRange range = new AbsoluteRange(new DateTime(2015, 1, 1, 0, 0), new DateTime(2015, 1, 2, 0, 0));
        HistogramResult h = searches.fieldHistogram("*", "n", Searches.DateHistogramInterval.MINUTE, null, range);

        Assertions.assertThat(h.getInterval()).isEqualTo(Searches.DateHistogramInterval.MINUTE);
        Assertions.assertThat(h.getHistogramBoundaries()).isEqualTo(range);
        Assertions.assertThat(h.getResults()).hasSize(5);
        Assertions.assertThat((Map<String, Number>) h.getResults().get(new DateTime(2015, 1, 1, 0, 1, DateTimeZone.UTC).getMillis() / 1000L))
                .containsEntry("total_count", 2L)
                .containsEntry("total", 0.0);
        Assertions.assertThat((Map<String, Number>) h.getResults().get(new DateTime(2015, 1, 1, 0, 2, DateTimeZone.UTC).getMillis() / 1000L))
                .containsEntry("total_count", 2L)
                .containsEntry("total", 4.0)
                .containsEntry("mean", 2.0);
    }
}