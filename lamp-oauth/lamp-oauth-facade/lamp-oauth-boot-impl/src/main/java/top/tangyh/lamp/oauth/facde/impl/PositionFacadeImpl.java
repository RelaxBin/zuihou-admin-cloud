package top.tangyh.lamp.oauth.facde.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.tangyh.lamp.base.service.user.BasePositionService;
import top.tangyh.lamp.model.constant.EchoApi;
import top.tangyh.lamp.oauth.facade.PositionFacade;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

/**
 * 实现
 * @author tangyh
 * @since 2024/9/20 23:29
 */
@Service(EchoApi.POSITION_ID_CLASS)
@RequiredArgsConstructor
public class PositionFacadeImpl implements PositionFacade {
    private final BasePositionService basePositionService;

    @Override
    public Map<Serializable, Object> findByIds(Set<Serializable> ids) {
        return basePositionService.findByIds(ids);
    }
}
