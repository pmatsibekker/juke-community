package org.juke.remix.service;

import org.juke.framework.config.ConfigUtil;
import org.juke.framework.proxy.JukeFactory;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.proxy.ReplayHandler;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link ReplayService}. Flips the global Juke
 * state to {@link JukeState#REPLAY} and installs a DAO pointed at the
 * whitelisted track ZIP. {@link #enable()} / {@link #disable()} toggle the
 * global disable flag without tearing down the underlying DAO.
 *
 * <p>Profile-gated to {@code record} and {@code replay} so production builds
 * don't instantiate the replay stack.
 */
@Service
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class ReplayServiceImpl implements ReplayService {

    @Override
    public String start(String track) {
        String whiteListed = RemixUtil.getWhiteList(track);
        if (whiteListed == null) {
            return "NOK- " + track + " is not a valid test";
        }
        JukeFactory.setGlobaljuke(JukeState.REPLAY);
        JukeZipDAOImpl impl = new JukeZipDAOImpl(ConfigUtil.getJukePath(), whiteListed);
        JukeHelper.setJukeDao(impl);
        JukeFactory.resetReplay();
        if (!impl.exists()) {
            return RemixUtil.NOK;
        }
        return RemixUtil.OK;
    }

    @Override
    public String enable() {
        JukeFactory.setGlobaljuke(JukeState.REPLAY);
        JukeState.setGlobalDisable(false);
        return RemixUtil.OK;
    }

    @Override
    public String disable() {
        JukeFactory.setGlobaljuke(JukeState.REPLAY);
        JukeState.setGlobalDisable(true);
        ReplayHandler.resetReplay();
        return RemixUtil.OK;
    }
}
