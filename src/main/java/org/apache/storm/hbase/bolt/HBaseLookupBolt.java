/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.hbase.bolt;

import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.apache.commons.lang.Validate;
import org.apache.hadoop.hbase.client.*;
import org.apache.storm.hbase.bolt.mapper.HBaseMapper;
import org.apache.storm.hbase.bolt.mapper.HBaseProjectionCriteria;
import org.apache.storm.hbase.bolt.mapper.HBaseRowToStormValueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Basic bolt for querying from HBase.
 *
 * Note: Each HBaseBolt defined in a topology is tied to a specific table.
 *
 */
public class HBaseLookupBolt extends AbstractHBaseBolt {
    private static final Logger LOG = LoggerFactory.getLogger(HBaseLookupBolt.class);

    private HBaseRowToStormValueMapper rowToTupleMapper;

    private HBaseProjectionCriteria projectionCriteria;

    public HBaseLookupBolt(String tableName, HBaseMapper mapper, HBaseRowToStormValueMapper rowToTupleMapper){
        super(tableName, mapper);
        Validate.notNull(rowToTupleMapper, "rowToTupleMapper can not be null");
        this.rowToTupleMapper = rowToTupleMapper;
    }

    public HBaseLookupBolt withConfigKey(String configKey){
        this.configKey = configKey;
        return this;
    }

    public HBaseLookupBolt withProjectionCriteria(HBaseProjectionCriteria projectionCriteria) {
        this.projectionCriteria = projectionCriteria;
        return this;
    }

    @Override
    public void execute(Tuple tuple) {
        byte[] rowKey = this.mapper.rowKey(tuple);
        try {
            Get get = new Get(rowKey);
            if(projectionCriteria != null) {
                for (byte[] columnFamily : projectionCriteria.getColumnFamilies()) {
                    get.addFamily(columnFamily);
                }

                for (HBaseProjectionCriteria.ColumnMetaData columnMetaData : projectionCriteria.getColumns()) {
                    get.addColumn(columnMetaData.getColumnFamily(), columnMetaData.getQualifier());
                }
            }

            Result result = table.get(get);
            for(Values values : rowToTupleMapper.toValues(result)) {
                this.collector.emit(values);
            }
            this.collector.ack(tuple);
        } catch (Exception e) {
            LOG.warn("Could not perform Lookup for rowKey =" + rowKey + " from Hbase.", e);
            this.collector.fail(tuple);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        rowToTupleMapper.declareOutputFields(outputFieldsDeclarer);
    }
}
