package org.example.myplatform.xiaoxiaole.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.example.myplatform.xiaoxiaole.entity.XiaoxiaoleRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XiaoxiaoleRecordMapper extends BaseMapper<XiaoxiaoleRecord> {

    /**
     * 根据用户ID查询记录
     */
    @Select("SELECT * FROM xiaoxiaole_record WHERE user_id = #{userId}")
    XiaoxiaoleRecord selectByUserId(Long userId);

    /**
     * 查询排行榜（按最高关卡降序，分数降序）
     */
    @Select("SELECT * FROM xiaoxiaole_record ORDER BY highest_level DESC, best_score DESC LIMIT 100")
    List<XiaoxiaoleRecord> selectRankList();

    /**
     * 查询用户排名
     */
    @Select("SELECT COUNT(*) + 1 FROM xiaoxiaole_record WHERE highest_level > (SELECT highest_level FROM xiaoxiaole_record WHERE user_id = #{userId}) OR (highest_level = (SELECT highest_level FROM xiaoxiaole_record WHERE user_id = #{userId}) AND best_score > (SELECT best_score FROM xiaoxiaole_record WHERE user_id = #{userId}))")
    Integer selectUserRank(Long userId);
}