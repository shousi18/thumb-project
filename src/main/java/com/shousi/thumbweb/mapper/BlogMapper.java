package com.shousi.thumbweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shousi.thumbweb.model.entity.Blog;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * @author 86172
 * @description 针对表【blog】的数据库操作Mapper
 * @createDate 2025-04-20 21:08:45
 * @Entity com.shousi.thumbweb.model.entity.Blog
 */
public interface BlogMapper extends BaseMapper<Blog> {

    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}




