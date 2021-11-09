// This file is licensed under the Elastic License 2.0. Copyright 2021 StarRocks Limited.

#pragma once

#include <vector>

#include "exec/vectorized/file_scanner.h"
#include "gen_cpp/AgentService_types.h"
#include "gen_cpp/MasterService_types.h"
#include "runtime/descriptors.h"
#include "storage/olap_common.h"
#include "storage/rowset/rowset.h"
#include "storage/tablet.h"

namespace starrocks::vectorized {

struct TabletVars {
    TabletSharedPtr tablet;
    RowsetSharedPtr rowset_to_add;
};

class PushBrokerReader {
public:
    PushBrokerReader() = default;
    ~PushBrokerReader() {
        _counter.reset();
        _scanner.reset();
    }

    Status init(const TBrokerScanRange& t_scan_range, const TDescriptorTable& t_desc_tbl);
    Status next_chunk(ChunkPtr* chunk);

    void print_profile();

    Status close() {
        _ready = false;
        return Status::OK();
    }
    bool eof() const { return _eof; }

private:
    // convert chunk that read from parquet scanner to chunk that write to rowset writer
    // 1. column is nullable or not should be determined by schema.
    // 2. deserialize bitmap and hll column from varchar that read from parquet file.
    // 3. padding char column.
    Status _convert_chunk(const ChunkPtr& from, ChunkPtr* to);
    ColumnPtr _build_object_column(const ColumnPtr& column);
    ColumnPtr _build_hll_column(const ColumnPtr& column);
    ColumnPtr _padding_char_column(const ColumnPtr& column, const SlotDescriptor* slot_desc, size_t num_rows);

    bool _ready = false;
    bool _eof = false;
    std::unique_ptr<RuntimeState> _runtime_state;
    RuntimeProfile* _runtime_profile = nullptr;
    TupleDescriptor* _tuple_desc = nullptr;
    std::unique_ptr<ScannerCounter> _counter;
    std::unique_ptr<FileScanner> _scanner;
};

// Vectorized push handler for spark load.
// The parquet files generated by spark dpp are divided by tablet and the data is sorted,
// so the push handler reads the parquet file through the broker and directly writes the rowset.
class PushHandler {
public:
    PushHandler() = default;
    ~PushHandler() = default;

    // Load local data file into specified tablet.
    Status process_streaming_ingestion(const TabletSharedPtr& tablet, const TPushReq& request, PushType push_type,
                                       std::vector<TTabletInfo>* tablet_info_vec);

    int64_t write_bytes() const { return _write_bytes; }
    int64_t write_rows() const { return _write_rows; }

private:
    Status _do_streaming_ingestion(TabletSharedPtr tablet, const TPushReq& request, PushType push_type,
                                   vector<TabletVars>* tablet_vars, std::vector<TTabletInfo>* tablet_info_vec);

    void _get_tablet_infos(const std::vector<TabletVars>& tablet_infos, std::vector<TTabletInfo>* tablet_info_vec);

    Status _convert(const TabletSharedPtr& cur_tablet, const TabletSharedPtr& new_tablet_vec,
                    RowsetSharedPtr* cur_rowset, RowsetSharedPtr* new_rowset);

private:
    // mainly tablet_id, version and delta file path
    TPushReq _request;

    int64_t _write_bytes = 0;
    int64_t _write_rows = 0;
};
} // namespace starrocks::vectorized
