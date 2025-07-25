package forge.adventure.scene;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.google.common.collect.Lists;
import forge.Forge;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.pointofintrest.PointOfInterestChanges;
import forge.adventure.stage.MapStage;
import forge.adventure.stage.PointOfInterestMapRenderer;
import forge.adventure.stage.WorldStage;
import forge.adventure.util.*;
import forge.adventure.world.WorldSave;
import forge.sound.SoundEffectType;
import forge.sound.SoundSystem;

import java.util.ArrayList;

/**
 * Scene that will render tiled maps.
 * Used for towns dungeons etc
 */
public class TileMapScene extends HudScene {
    TiledMap map;
    PointOfInterestMapRenderer tiledMapRenderer;
    private String nextMap;
    private int nextSpawnPoint;
    private boolean autoheal = false;

    private TileMapScene() {
        super(MapStage.getInstance());
        tiledMapRenderer = new PointOfInterestMapRenderer((MapStage) stage);

        //set initial camera width and height
        MapStage.getInstance().setDialogStage(hud);
    }

    private static TileMapScene object;

    public static TileMapScene instance() {
        if (object == null)
            object = new TileMapScene();
        return object;
    }

    public MapStage currentMap() {
        return (MapStage) stage;
    }

    @Override
    public void dispose() {
        if (map != null)
            map.dispose();
    }

    @Override
    public void act(float delta) {
        if (map == null)
            return;
        if (nextMap != null) {
            load(nextMap, nextSpawnPoint);
            nextMap = null;
            nextSpawnPoint = 0;
        }
        stage.act(Gdx.graphics.getDeltaTime());
        hud.act(Gdx.graphics.getDeltaTime());
        if (autoheal) {
            stage.getPlayerSprite().playEffect(Paths.EFFECT_HEAL, 2);
            SoundSystem.instance.play(SoundEffectType.Enchantment, false);
            autoheal = false;
        }
    }

    @Override
    public void render() {
        if (map == null)
            return;
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        tiledMapRenderer.setView(stage.getCamera().combined, stage.getCamera().position.x - Scene.getIntendedWidth() / 2.0f, stage.getCamera().position.y - Scene.getIntendedHeight() / 2.0f, Scene.getIntendedWidth(), Scene.getIntendedHeight());

        if (!Forge.isLandscapeMode()) {
            stage.getCamera().position.x = stage.getPlayerSprite().pos().x;
        }
        tiledMapRenderer.render();
        hud.draw();
    }


    @Override
    public void enter() {
        super.enter();
        if (isAutoHealLocation()) {
            // auto heal
            if (Current.player().fullHeal())
                autoheal = true; // to play sound/effect on act
        }
        if (WorldSave.getCurrentSave().getPlayer().hasAnnounceFantasy()) {
            WorldSave.getCurrentSave().getPlayer().clearAnnounceFantasy();
            MapStage.getInstance().showDeckAwardDialog("{BLINK=WHITE;RED}" +
                    Forge.getLocalizer().getMessage("lblMode") + " " +
                    Forge.getLocalizer().getMessage("lblChaos") + "{ENDBLINK}\n" +
                    Forge.getLocalizer().getMessage("lblChaosModeDescription"),
                    WorldSave.getCurrentSave().getPlayer().getSelectedDeck(), this::initializeDialogs);
        } else if (WorldSave.getCurrentSave().getPlayer().hasAnnounceCustom()) {
            WorldSave.getCurrentSave().getPlayer().clearAnnounceCustom();
            MapStage.getInstance().showDeckAwardDialog("{GRADIENT}" +
                    Forge.getLocalizer().getMessage("lblMode") + " " +
                    Forge.getLocalizer().getMessage("lblCustom") + "{ENDGRADIENT}\n" +
                    Forge.getLocalizer().getMessage("lblCustomModeDescription"),
                    WorldSave.getCurrentSave().getPlayer().getSelectedDeck(), this::initializeDialogs);
        } else {
            initializeDialogs();
        }
    }

    private void initializeDialogs() {
        AdventureQuestController.instance().updateEnteredPOI(rootPoint);
        AdventureQuestController.instance().showQuestDialogs(stage);
    }
    @Override
    public boolean leave() {
        // clear player collision on WorldStage and the GameHUD will restore it after the flicker animation.
        // There's at least 2 seconds to get away from problematic collision point and player can retry
        // a few times to move to different position if the POI is loaded again from WorldStage
        WorldStage.getInstance().getPlayerSprite().clearCollisionHeight();
        return super.leave();
    }

    public void load(PointOfInterest point) {
        AdventureQuestController.instance().mostRecentPOI = point;
        rootPoint = point;
        oldMap = point.getData().map;
        map = new TemplateTmxMapLoader().load(Config.instance().getCommonFilePath(point.getData().map));
        ((MapStage) stage).setPointOfInterest(getPointOfInterestChanges());
        stage.getPlayerSprite().setPosition(0, 0);
        WorldSave.getCurrentSave().getWorld().setSeed(point.getSeedOffset());
        tiledMapRenderer.loadMap(map, "", oldMap, 0);
        stage.getPlayerSprite().stop();
    }

    private final static ArrayList<String> AUTO_HEAL_LOCATIONS = Lists.newArrayList("capital", "town");

    public boolean isAutoHealLocation() {
        return AUTO_HEAL_LOCATIONS.contains(rootPoint.getData().type);
    }

    public PointOfInterest rootPoint;
    String oldMap;

    private void load(String targetMap, int nextSpawnPoint) {
        map = new TemplateTmxMapLoader().load(Config.instance().getFilePath(targetMap));
        ((MapStage) stage).setPointOfInterest(getPointOfInterestChanges(targetMap));
        stage.getPlayerSprite().setPosition(0, 0);
        WorldSave.getCurrentSave().getWorld().setSeed(rootPoint.getSeedOffset());
        tiledMapRenderer.loadMap(map, oldMap, targetMap, nextSpawnPoint);
        oldMap = targetMap;
        stage.getPlayerSprite().stop();
    }

    public PointOfInterestChanges getPointOfInterestChanges() {
        return WorldSave.getCurrentSave().getPointOfInterestChanges(rootPoint.getID());
    }

    public PointOfInterestChanges getPointOfInterestChanges(String targetMap) {
        if (rootPoint.getID().endsWith(targetMap))
            return getPointOfInterestChanges();
        return WorldSave.getCurrentSave().getPointOfInterestChanges(rootPoint.getID() + targetMap);
    }


    @Override
    public boolean isInHudOnlyMode() {
        return MapStage.getInstance().isDialogOnlyInput();
    }

    public void loadNext(String targetMap, int entryTargetObject) {
        nextMap = targetMap;
        nextSpawnPoint = entryTargetObject;
    }
}

