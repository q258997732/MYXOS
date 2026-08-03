package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.SysUser;
import bob.myxos.domain.mapper.SysUserMapper;
import bob.myxos.main.dto.UserCreateReq;
import bob.myxos.main.dto.UserUpdateReq;
import bob.myxos.main.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 系统用户业务实现
 */
@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SysUser> list(Long page, Long size) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDeleted, 0);
        wrapper.orderByDesc(SysUser::getId);
        return sysUserMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser create(UserCreateReq req) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername())
                        .eq(SysUser::getDeleted, 0));
        if (count != null && count > 0) {
            throw new BizException("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setRole(req.getRole());
        user.setStatus(1);
        user.setWhenCreated(LocalDateTime.now());
        user.setWhenModified(LocalDateTime.now());
        sysUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysUser update(Long id, UserUpdateReq req) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BizException("用户不存在");
        }
        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getRole() != null) {
            user.setRole(req.getRole());
        }
        if (req.getStatus() != null) {
            user.setStatus(req.getStatus());
        }
        user.setWhenModified(LocalDateTime.now());
        sysUserMapper.updateById(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String rawPassword) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BizException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setWhenModified(LocalDateTime.now());
        sysUserMapper.updateById(user);
    }
}
