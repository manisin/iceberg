/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.TimeUnit;
import org.apache.iceberg.Schema;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.metrics.ScanReport.ScanMetrics;
import org.apache.iceberg.types.Types;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class TestScanReportParser {

  @Test
  public void nullScanReport() {
    Assertions.assertThatThrownBy(() -> ScanReportParser.fromJson((JsonNode) null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse scan report from null object");

    Assertions.assertThatThrownBy(() -> ScanReportParser.toJson(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid scan report: null");
  }

  @Test
  public void missingFields() {
    Assertions.assertThatThrownBy(() -> ScanReportParser.fromJson("{}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing string: table-name");

    Assertions.assertThatThrownBy(
            () -> ScanReportParser.fromJson("{\"table-name\":\"roundTripTableName\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing long: snapshot-id");

    Assertions.assertThatThrownBy(
            () ->
                ScanReportParser.fromJson(
                    "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":23,\"filter\":true}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing field: projection");

    Assertions.assertThatThrownBy(
            () ->
                ScanReportParser.fromJson(
                    "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":23,\"filter\":true,"
                        + "\"projection\":{\"type\":\"struct\",\"schema-id\":0,\"fields\":[{\"id\":1,\"name\":\"c1\",\"required\":true,\"type\":\"string\",\"doc\":\"c1\"}]}}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse missing field: metrics");
  }

  @Test
  public void extraFields() {
    ScanReport.ScanMetrics scanMetrics = new ScanReport.ScanMetrics(new DefaultMetricsContext());
    scanMetrics.totalPlanningDuration().record(10, TimeUnit.MINUTES);
    scanMetrics.resultDataFiles().increment(5L);
    scanMetrics.resultDeleteFiles().increment(5L);
    scanMetrics.scannedDataManifests().increment(5L);
    scanMetrics.skippedDataManifests().increment(5L);
    scanMetrics.totalFileSizeInBytes().increment(1024L);
    scanMetrics.totalDataManifests().increment(5L);
    scanMetrics.totalFileSizeInBytes().increment(45L);
    scanMetrics.totalDeleteFileSizeInBytes().increment(23L);

    String tableName = "roundTripTableName";
    Schema projection =
        new Schema(Types.NestedField.required(1, "c1", Types.StringType.get(), "c1"));
    ScanReport scanReport =
        ScanReport.builder()
            .withTableName(tableName)
            .withProjection(projection)
            .withSnapshotId(23L)
            .withFilter(Expressions.alwaysTrue())
            .fromScanMetrics(scanMetrics)
            .build();

    Assertions.assertThat(
            ScanReportParser.fromJson(
                "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":23,"
                    + "\"filter\":true,\"projection\":{\"type\":\"struct\",\"schema-id\":0,\"fields\":[{\"id\":1,\"name\":\"c1\",\"required\":true,\"type\":\"string\",\"doc\":\"c1\"}]},"
                    + "\"metrics\":{\"total-planning-duration\":{\"count\":1,\"time-unit\":\"nanoseconds\",\"total-duration\":600000000000},"
                    + "\"result-data-files\":{\"unit\":\"count\",\"value\":5},"
                    + "\"result-delete-files\":{\"unit\":\"count\",\"value\":5},"
                    + "\"total-data-manifests\":{\"unit\":\"count\",\"value\":5},"
                    + "\"total-delete-manifests\":{\"unit\":\"count\",\"value\":0},"
                    + "\"scanned-data-manifests\":{\"unit\":\"count\",\"value\":5},"
                    + "\"skipped-data-manifests\":{\"unit\":\"count\",\"value\":5},"
                    + "\"total-file-size-in-bytes\":{\"unit\":\"bytes\",\"value\":1069},"
                    + "\"total-delete-file-size-in-bytes\":{\"unit\":\"bytes\",\"value\":23},"
                    + "\"extra-metric\":\"extra-val\"},"
                    + "\"extra\":\"extraVal\"}"))
        .usingRecursiveComparison()
        .ignoringFields("projection")
        .isEqualTo(scanReport);
  }

  @Test
  public void invalidTableName() {
    Assertions.assertThatThrownBy(() -> ScanReportParser.fromJson("{\"table-name\":23}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a string value: table-name: 23");
  }

  @Test
  public void invalidSnapshotId() {
    Assertions.assertThatThrownBy(
            () ->
                ScanReportParser.fromJson(
                    "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":\"invalid\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse to a long value: snapshot-id: \"invalid\"");
  }

  @Test
  public void invalidExpressionFilter() {
    Assertions.assertThatThrownBy(
            () ->
                ScanReportParser.fromJson(
                    "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":23,\"filter\":23,\"projection\":23}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse expression from non-object: 23");
  }

  @Test
  public void invalidSchema() {
    Assertions.assertThatThrownBy(
            () ->
                ScanReportParser.fromJson(
                    "{\"table-name\":\"roundTripTableName\",\"snapshot-id\":23,\"filter\":true,\"projection\":23}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cannot parse type from json: 23");
  }

  @Test
  public void roundTripSerde() {
    ScanReport.ScanMetrics scanMetrics = new ScanReport.ScanMetrics(new DefaultMetricsContext());
    scanMetrics.totalPlanningDuration().record(10, TimeUnit.MINUTES);
    scanMetrics.resultDataFiles().increment(5L);
    scanMetrics.resultDeleteFiles().increment(5L);
    scanMetrics.scannedDataManifests().increment(5L);
    scanMetrics.skippedDataManifests().increment(5L);
    scanMetrics.totalFileSizeInBytes().increment(1024L);
    scanMetrics.totalDataManifests().increment(5L);
    scanMetrics.totalFileSizeInBytes().increment(45L);
    scanMetrics.totalDeleteFileSizeInBytes().increment(23L);

    String tableName = "roundTripTableName";
    Schema projection =
        new Schema(Types.NestedField.required(1, "c1", Types.StringType.get(), "c1"));
    ScanReport scanReport =
        ScanReport.builder()
            .withTableName(tableName)
            .withProjection(projection)
            .withSnapshotId(23L)
            .withFilter(Expressions.alwaysTrue())
            .fromScanMetrics(scanMetrics)
            .build();

    String expectedJson =
        "{\n"
            + "  \"table-name\" : \"roundTripTableName\",\n"
            + "  \"snapshot-id\" : 23,\n"
            + "  \"filter\" : true,\n"
            + "  \"projection\" : {\n"
            + "    \"type\" : \"struct\",\n"
            + "    \"schema-id\" : 0,\n"
            + "    \"fields\" : [ {\n"
            + "      \"id\" : 1,\n"
            + "      \"name\" : \"c1\",\n"
            + "      \"required\" : true,\n"
            + "      \"type\" : \"string\",\n"
            + "      \"doc\" : \"c1\"\n"
            + "    } ]\n"
            + "  },\n"
            + "  \"metrics\" : {\n"
            + "    \"total-planning-duration\" : {\n"
            + "      \"count\" : 1,\n"
            + "      \"time-unit\" : \"nanoseconds\",\n"
            + "      \"total-duration\" : 600000000000\n"
            + "    },\n"
            + "    \"result-data-files\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 5\n"
            + "    },\n"
            + "    \"result-delete-files\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 5\n"
            + "    },\n"
            + "    \"total-data-manifests\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 5\n"
            + "    },\n"
            + "    \"total-delete-manifests\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 0\n"
            + "    },\n"
            + "    \"scanned-data-manifests\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 5\n"
            + "    },\n"
            + "    \"skipped-data-manifests\" : {\n"
            + "      \"unit\" : \"count\",\n"
            + "      \"value\" : 5\n"
            + "    },\n"
            + "    \"total-file-size-in-bytes\" : {\n"
            + "      \"unit\" : \"bytes\",\n"
            + "      \"value\" : 1069\n"
            + "    },\n"
            + "    \"total-delete-file-size-in-bytes\" : {\n"
            + "      \"unit\" : \"bytes\",\n"
            + "      \"value\" : 23\n"
            + "    }\n"
            + "  }\n"
            + "}";

    String json = ScanReportParser.toJson(scanReport, true);
    Assertions.assertThat(ScanReportParser.fromJson(json))
        .usingRecursiveComparison()
        .ignoringFields("projection")
        .isEqualTo(scanReport);
    Assertions.assertThat(json).isEqualTo(expectedJson);
  }

  @Test
  public void roundTripSerdeWithNoopMetrics() {
    String tableName = "roundTripTableName";
    Schema projection =
        new Schema(Types.NestedField.required(1, "c1", Types.StringType.get(), "c1"));
    ScanReport scanReport =
        ScanReport.builder()
            .withTableName(tableName)
            .withProjection(projection)
            .withSnapshotId(23L)
            .withFilter(Expressions.alwaysTrue())
            .fromScanMetrics(ScanMetrics.NOOP)
            .build();

    String expectedJson =
        "{\n"
            + "  \"table-name\" : \"roundTripTableName\",\n"
            + "  \"snapshot-id\" : 23,\n"
            + "  \"filter\" : true,\n"
            + "  \"projection\" : {\n"
            + "    \"type\" : \"struct\",\n"
            + "    \"schema-id\" : 0,\n"
            + "    \"fields\" : [ {\n"
            + "      \"id\" : 1,\n"
            + "      \"name\" : \"c1\",\n"
            + "      \"required\" : true,\n"
            + "      \"type\" : \"string\",\n"
            + "      \"doc\" : \"c1\"\n"
            + "    } ]\n"
            + "  },\n"
            + "  \"metrics\" : { }\n"
            + "}";

    String json = ScanReportParser.toJson(scanReport, true);
    Assertions.assertThat(ScanReportParser.fromJson(json))
        .usingRecursiveComparison()
        .ignoringFields("projection")
        .isEqualTo(scanReport);
    Assertions.assertThat(json).isEqualTo(expectedJson);
  }
}
