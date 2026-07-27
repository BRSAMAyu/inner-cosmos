package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.SocialGroup;
import com.innercosmos.entity.SocialGroupMember;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.SocialGroupMapper;
import com.innercosmos.mapper.SocialGroupMemberMapper;
import com.innercosmos.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One opt-in room for all registered HUMAN attendees in the temporary classroom Demo. */
@Service
@Order(200)
@ConditionalOnProperty(prefix = "inner-cosmos.demo", name = "public-entry-enabled", havingValue = "true")
public class PublicDemoClassroomGroupService implements ApplicationRunner {
    public static final String GROUP_NAME = "Inner Cosmos · 现场共同星球";
    private static final String VISIBILITY = "CLASSROOM";

    private final UserMapper userMapper;
    private final SocialGroupMapper groupMapper;
    private final SocialGroupMemberMapper memberMapper;

    public PublicDemoClassroomGroupService(UserMapper userMapper, SocialGroupMapper groupMapper,
                                           SocialGroupMemberMapper memberMapper) {
        this.userMapper = userMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureGroup();
    }

    @Transactional(rollbackFor = Exception.class)
    public SocialGroup join(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !"HUMAN".equals(user.accountKind)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有现场注册的真实用户可以加入共同小组");
        }
        SocialGroup group = findGroup();
        if (group == null) group = ensureGroup();
        SocialGroupMember member = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                .eq("group_id", group.id).eq("user_id", userId).last("LIMIT 1"));
        if (member == null) {
            member = new SocialGroupMember();
            member.groupId = group.id;
            member.userId = userId;
            member.memberRole = "MEMBER";
            member.status = "ACTIVE";
            try {
                memberMapper.insert(member);
            } catch (DuplicateKeyException concurrentJoin) {
                member = memberMapper.selectOne(new QueryWrapper<SocialGroupMember>()
                        .eq("group_id", group.id).eq("user_id", userId).last("LIMIT 1"));
            }
        }
        if (member != null && !"ACTIVE".equals(member.status)) {
            member.status = "ACTIVE";
            member.memberRole = "MEMBER";
            memberMapper.updateById(member);
        }
        return group;
    }

    private synchronized SocialGroup ensureGroup() {
        SocialGroup existing = findGroup();
        if (existing != null) return existing;
        User owner = userMapper.selectOne(new QueryWrapper<User>()
                .eq("username", "demo").eq("account_kind", "DEMO").last("LIMIT 1"));
        if (owner == null) throw new IllegalStateException("Curated Demo owner is missing");
        SocialGroup group = new SocialGroup();
        group.ownerUserId = owner.id;
        group.groupName = GROUP_NAME;
        group.intro = "全场共同的小组：用真实用户名出现，自愿加入，自由交流；消息不会进入 Aurora 私密记忆。";
        group.visibility = VISIBILITY;
        groupMapper.insert(group);
        SocialGroupMember member = new SocialGroupMember();
        member.groupId = group.id;
        member.userId = owner.id;
        member.memberRole = "OWNER";
        member.status = "ACTIVE";
        memberMapper.insert(member);
        return group;
    }

    private SocialGroup findGroup() {
        return groupMapper.selectOne(new QueryWrapper<SocialGroup>()
                .eq("visibility", VISIBILITY).eq("group_name", GROUP_NAME).last("LIMIT 1"));
    }
}
