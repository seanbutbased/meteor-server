package minegame159.meteorbot.webserver.controllers;

import minegame159.meteorbot.Config;
import minegame159.meteorbot.MeteorBot;
import minegame159.meteorbot.database.Db;
import minegame159.meteorbot.database.documents.DailyStats;
import minegame159.meteorbot.database.documents.Stats;
import minegame159.meteorbot.utils.Utils;
import minegame159.meteorbot.webserver.WebServer;
import minegame159.meteorbot.webserver.WebsiteVisits;
import net.dv8tion.jda.api.entities.VoiceChannel;
import org.apache.velocity.VelocityContext;
import spark.Route;

import java.io.InputStream;
import java.io.OutputStream;

import static com.mongodb.client.model.Updates.inc;

public class MainController {
    private static int DOWNLOADS;
    private static int updateDownloadsChannelCounter = 0;

    public static void init() {
        DOWNLOADS = Db.GLOBAL.get(Stats.class, Stats.ID).downloads;
    }

    public static Route SERVE_INDEX = (request, response) -> {
        VelocityContext context = new VelocityContext();

        context.put("version", Config.VERSION);
        context.put("mcVersion", Config.MC_VERSION);
        context.put("onlinePlayers", ApiController.getOnlinePlayers());
        context.put("downloads", DOWNLOADS);

        context.put("minegame", MeteorBot.MINEGAME.getAvatarUrl());
        context.put("squid", MeteorBot.SQUID.getAvatarUrl());
        context.put("seasnail", MeteorBot.SEASNAIL.getAvatarUrl());

        WebsiteVisits.increment(request);
        return WebServer.render(context, "views/index.html");
    };

    public static Route SERVE_INFO = (request, response) -> {
        VelocityContext context = new VelocityContext();

        context.put("version", Config.VERSION);
        context.put("mcVersion", Config.MC_VERSION);
        context.put("changelog", Config.CHANGELOG);

        WebsiteVisits.increment(request);
        return WebServer.render(context, "views/info.html");
    };

    public static Route HANDLE_DOWNLOAD = (request, response) -> {
        Long lastDownloadTime = request.session().attribute("lastDownloadTime");
        long time = System.currentTimeMillis();
        
        if (lastDownloadTime == null || time - lastDownloadTime > 60 * 1000) {
            Db.GLOBAL.update(Stats.ID, inc("downloads", 1));

            DailyStats dailyStats = Db.DAILY_STATS.get(Utils.getDateString());
            if (dailyStats == null) dailyStats = new DailyStats(Utils.getDateString(), 0, 0);
            dailyStats.downloads++;
            Db.DAILY_STATS.update(dailyStats);
            DOWNLOADS++;

            updateDownloadsChannelCounter++;
            if (updateDownloadsChannelCounter > 10) {
                VoiceChannel channel = Utils.findVoiceChannel(MeteorBot.JDA.getGuildById("689197705683140636"), "Downloads: ");
                if (channel != null) channel.getManager().setName("Downloads: " + DOWNLOADS).queue();
                updateDownloadsChannelCounter = 0;
            }
            
            request.session().attribute("lastDownloadTime", time);
        }

        String devBuild = request.queryParams("devBuild");
        if (devBuild != null) {
            response.redirect("https://" + devBuild + "-309730396-gh.circle-artifacts.com/0/build/libs/meteor-client-" + Config.VERSION + "-" + devBuild + ".jar");
            return null;
        } else {
            response.header("Content-Disposition", "attachment; filename=meteor-client-" + Config.VERSION + ".jar");
            response.type("application/java-archive");

            OutputStream out = response.raw().getOutputStream();
            InputStream in = WebServer.class.getResourceAsStream("/meteor-client-" + Config.VERSION + ".mjar");
            Utils.copyStream(in, out);

            return response.raw();
        }
    };
}
