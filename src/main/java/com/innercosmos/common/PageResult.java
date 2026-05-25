package com.innercosmos.common;

import java.util.List;

public class PageResult<T> {
    public long total;
    public List<T> records;

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records;
    }
}
