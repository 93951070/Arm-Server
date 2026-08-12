package armadillo.admin.service;

import armadillo.mapper.SysCardMapper;
import armadillo.mapper.SysNoticeMapper;
import armadillo.mapper.SysUserMapper;
import armadillo.mapper.SysVerMapper;
import armadillo.model.SysCard;
import armadillo.model.SysNotice;
import armadillo.model.SysUser;
import armadillo.model.SysVer;
import armadillo.utils.CardRadom;
import armadillo.utils.MyBatisUtil;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", 0);
        stats.put("activeUsers", 0);
        stats.put("expiredUsers", 0);
        stats.put("todayNewUsers", 0);
        stats.put("totalCards", 0);
        stats.put("unusedCards", 0);
        stats.put("noticeCount", 0);
        stats.put("serverTime", new Date());
        stats.put("dbError", null);

        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysUserMapper userMapper = sqlSession.getMapper(SysUserMapper.class);
            List<SysUser> users = userMapper.selectAll();

            int activeUsers = 0;
            int expiredUsers = 0;
            int todayNewUsers = 0;

            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            long todayStart = today.getTimeInMillis();

            for (SysUser user : users) {
                if (user.getExpireTime() != null) {
                    if (user.getExpireTime().getTime() > System.currentTimeMillis()) {
                        activeUsers++;
                    } else {
                        expiredUsers++;
                    }
                }
                if (user.getRegTime() != null && user.getRegTime().getTime() >= todayStart) {
                    todayNewUsers++;
                }
            }

            stats.put("totalUsers", users.size());
            stats.put("activeUsers", activeUsers);
            stats.put("expiredUsers", expiredUsers);
            stats.put("todayNewUsers", todayNewUsers);

            SysCardMapper cardMapper = sqlSession.getMapper(SysCardMapper.class);
            List<SysCard> cards = cardMapper.selectAll();
            int unusedCards = 0;
            for (SysCard card : cards) {
                if (card.getUsable() != null && card.getUsable()) {
                    unusedCards++;
                }
            }
            stats.put("totalCards", cards.size());
            stats.put("unusedCards", unusedCards);

            SysNoticeMapper noticeMapper = sqlSession.getMapper(SysNoticeMapper.class);
            List<SysNotice> notices = noticeMapper.selectAll();
            stats.put("noticeCount", notices.size());

        } catch (Exception e) {
            logger.error("getDashboardStats error", e);
            stats.put("dbError", "数据库连接失败: " + e.getMessage());
        }

        return stats;
    }

    public Map<String, Object> getUsers(int page, int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("users", Collections.emptyList());
        data.put("total", 0L);

        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysUserMapper userMapper = sqlSession.getMapper(SysUserMapper.class);
            List<SysUser> allUsers = userMapper.selectAll();
            int total = allUsers.size();
            int offset = page * size;
            int toIndex = Math.min(offset + size, total);
            List<SysUser> pageUsers = offset < total ? allUsers.subList(offset, toIndex) : Collections.emptyList();

            data.put("users", pageUsers);
            data.put("total", (long) total);

        } catch (Exception e) {
            logger.error("getUsers error", e);
            data.put("dbError", "数据库连接失败: " + e.getMessage());
        }

        return data;
    }

    public void banUser(int userId) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysUserMapper userMapper = sqlSession.getMapper(SysUserMapper.class);
            SysUser user = userMapper.selectByPrimaryKey(userId);
            if (user != null) {
                user.setExpireTime(new Date(System.currentTimeMillis()));
                user.setValue(0);
                userMapper.updateByPrimaryKey(user);
            }
        } catch (Exception e) {
            logger.error("banUser error", e);
        }
    }

    public void unbanUser(int userId, int days) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysUserMapper userMapper = sqlSession.getMapper(SysUserMapper.class);
            SysUser user = userMapper.selectByPrimaryKey(userId);
            if (user != null) {
                long newExpire = System.currentTimeMillis() + (long) days * 24 * 60 * 60 * 1000;
                user.setExpireTime(new Date(newExpire));
                userMapper.updateByPrimaryKey(user);
            }
        } catch (Exception e) {
            logger.error("unbanUser error", e);
        }
    }

    public void deleteUser(int userId) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysUserMapper userMapper = sqlSession.getMapper(SysUserMapper.class);
            userMapper.deleteByPrimaryKey(userId);
        } catch (Exception e) {
            logger.error("deleteUser error", e);
        }
    }

    public Map<String, Object> getCards(int page, int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("cards", Collections.emptyList());
        data.put("total", 0L);

        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysCardMapper cardMapper = sqlSession.getMapper(SysCardMapper.class);
            List<SysCard> allCards = cardMapper.selectAll();
            int total = allCards.size();
            int offset = page * size;
            int toIndex = Math.min(offset + size, total);
            List<SysCard> pageCards = offset < total ? allCards.subList(offset, toIndex) : Collections.emptyList();

            data.put("cards", pageCards);
            data.put("total", (long) total);

        } catch (Exception e) {
            logger.error("getCards error", e);
            data.put("dbError", "数据库连接失败: " + e.getMessage());
        }

        return data;
    }

    public List<String> createCards(int type, int count, int value) {
        List<String> cardKeys = new ArrayList<>();
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            List<SysCard> sysCards = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                SysCard card = new SysCard();
                card.setUsable(true);
                card.setType(type);
                card.setCount(value);
                String cardKey = CardRadom.radomCard();
                card.setCard(cardKey);
                cardKeys.add(cardKey);
                sysCards.add(card);
            }
            SysCardMapper cardMapper = sqlSession.getMapper(SysCardMapper.class);
            cardMapper.insertAll(sysCards);
        } catch (Exception e) {
            logger.error("createCards error", e);
        }
        return cardKeys;
    }

    public void deleteCard(int cardId) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysCardMapper cardMapper = sqlSession.getMapper(SysCardMapper.class);
            cardMapper.deleteByPrimaryKey(cardId);
        } catch (Exception e) {
            logger.error("deleteCard error", e);
        }
    }

    public Map<String, Object> getAllNotices() {
        Map<String, Object> data = new HashMap<>();
        data.put("notices", Collections.emptyList());

        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysNoticeMapper noticeMapper = sqlSession.getMapper(SysNoticeMapper.class);
            List<SysNotice> notices = noticeMapper.selectAll();
            data.put("notices", notices);
        } catch (Exception e) {
            logger.error("getAllNotices error", e);
            data.put("dbError", "数据库连接失败: " + e.getMessage());
        }

        return data;
    }

    public void addNotice(String title, String msg) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysNoticeMapper noticeMapper = sqlSession.getMapper(SysNoticeMapper.class);
            SysNotice notice = new SysNotice();
            notice.setTitle(title);
            notice.setMsg(msg);
            notice.setTime(new Date());
            noticeMapper.insert(notice);
        } catch (Exception e) {
            logger.error("addNotice error", e);
        }
    }

    public void deleteNotice(int id) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysNoticeMapper noticeMapper = sqlSession.getMapper(SysNoticeMapper.class);
            noticeMapper.deleteByPrimaryKey(id);
        } catch (Exception e) {
            logger.error("deleteNotice error", e);
        }
    }

    public Map<String, Object> getAllVersions() {
        Map<String, Object> data = new HashMap<>();
        data.put("versions", Collections.emptyList());

        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysVerMapper verMapper = sqlSession.getMapper(SysVerMapper.class);
            List<SysVer> versions = verMapper.selectAll();
            data.put("versions", versions);
        } catch (Exception e) {
            logger.error("getAllVersions error", e);
            data.put("dbError", "数据库连接失败: " + e.getMessage());
        }

        return data;
    }

    public void addVersion(int versionCode, String versionName, boolean forceUpdate, String msg) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysVerMapper verMapper = sqlSession.getMapper(SysVerMapper.class);
            SysVer ver = new SysVer();
            ver.setVersion(versionCode);
            ver.setVersionName(versionName);
            ver.setVersionMode(forceUpdate);
            ver.setTime(new Date());
            ver.setVersionMsg(msg);
            verMapper.insert(ver);
        } catch (Exception e) {
            logger.error("addVersion error", e);
        }
    }

    public void deleteVersion(int id) {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            SysVerMapper verMapper = sqlSession.getMapper(SysVerMapper.class);
            verMapper.deleteByPrimaryKey(id);
        } catch (Exception e) {
            logger.error("deleteVersion error", e);
        }
    }

    public void refreshCache() {
        try (SqlSession sqlSession = MyBatisUtil.getSqlSession(true)) {
            sqlSession.clearCache();
            for (Cache cache : sqlSession.getConfiguration().getCaches()) {
                cache.clear();
            }
        } catch (Exception e) {
            logger.error("refreshCache error", e);
        }
    }
}
