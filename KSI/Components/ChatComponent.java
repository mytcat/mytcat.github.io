package com.vg.cloud.js.components;

import com.vg.cloud.js.CloudServices;
import com.vg.cloud.js.LiveUpdate;
import com.vg.cloud.js.MediaGroupModel;
import com.vg.cloud.js.Requests;
import com.vg.cloud.js.TrackImport;
import com.vg.cloud.js.UIUtils;
import com.vg.cloud.js.model.ImportedTrack;
import com.vg.cloud.model.Media;
import com.vg.js.player.Timeline;
import com.vg.js.player.logging.model.Track;
import com.vg.js.player.logging.model.TrackData;
import org.stjs.bridge.react.Component;
import org.stjs.bridge.react.internal.ReactElement;
import org.stjs.bridge.react.internal.State;
import org.stjs.javascript.Array;
import org.stjs.javascript.dom.DOMEvent;
import org.stjs.javascript.dom.Div;
import org.stjs.javascript.dom.Element;
import org.stjs.javascript.dom.Input;
import org.stjs.javascript.functions.Callback1;

import static com.vg.cloud.model.Job.DOWNLOAD;
import static com.vg.cloud.model.Job.NO_CACHE;
import static org.stjs.bridge.react.React.DOM.*;
import static org.stjs.bridge.react.React.createElement;
import static org.stjs.javascript.Global.console;
import static org.stjs.javascript.JSCollections.$array;
import static org.stjs.javascript.JSCollections.$map;

public class ImportTrackComponent extends Component<ImportTrackComponent.ImportTrackProps, ImportTrackComponent.ImportTrackState> {

    public static final String ADD_BUTTON = "addButton";
    private Element parent;
    private MediaGroupModel model;

    public static class ImportTrackProps {
        public Timeline timeline;
        public LiveUpdate liveUpdate;
        public String masterId;

        public ImportTrackProps(Timeline timeline, LiveUpdate liveUpdate, String masterId) {
            this.timeline = timeline;
            this.liveUpdate = liveUpdate;
            this.masterId = masterId;
        }
    }

    public static class ImportTrackState extends State {
        public Callback1<Array<TrackData>> importCallback;
        public Array<TrackData> tracks;
    }

    public ImportTrackComponent(ImportTrackProps props) {
        super(props);
        state = initialState();
        model = new MediaGroupModel(props.liveUpdate);
        model.setMasterMediaId(props.masterId);
        model.connect();
        model.getMediaUpdates().subscribe(media -> {

        });
    }

    private ImportTrackState initialState() {
        return new ImportTrackState() {{
            tracks = $array();
            importCallback = ts -> {
            };
        }};
    }

    public void setImportCallback(Callback1<Array<TrackData>> importCallback) {
        state.importCallback = importCallback;
        setState(state);
    }

    @Override
    public ReactElement<?> render() {
        Callback1<DOMEvent> cancel = e -> {
            console.log("CANCEL");
            cancel();
        };
        Callback1<DOMEvent> doImport = e -> {
            console.log("SAVE");
            save();
        };
        Callback1<Div> elementRef = el -> {
            if (el != null) {
                parent = el.parentElement;
            }
        };
        Callback1<DOMEvent> keyDown = e -> {
            if (e.keyCode == 13) {
                Input input = (Input) e.target;
                if (input.value == null) {
                    return;
                }
                String url = input.value.trim();
                if ("".equals(url)) {
                    return;
                }
                submitUrl(url);
                input.value = "";
            }
        };

        Array<ReactElement> components = $array(div($map("className", "vg-select-input-row"),
                div($map("className", "box-select", "data-link-type", "direct", "data-multi-select", "true",
                        "data-client-id", "5r24jw3wvgzz0wk1mzo7htrbefs6ejmg")),
                div($map("className", "vg-select-or-input"), "OR"),
                div($map("className", "vg-url-input"),
                        input($map("placeholder", "Enter tracks URL...", "type", "url", "onKeyDown", keyDown))
                )
        ));
        if (state.tracks.$length() > 0) {
            components.push(
                    div($map("className", "vg-tracks-header"),
                            div($map("className", "vg-tracks"), "TRACKS"),
                            div($map("className", "vg-tracks-counter"), state.tracks.$length() + " tracks")
                    ),
                    div($map("className", "vg-imported-tracks"),
                            div($map("className", "vg-tracks-list"),
                                    div($map("className", "vg-imported-tracks-list"),
                                            tracks(state.tracks)
                                    )
                            )
                    )
            );
        }
        return div($map("className", "vg-add-track-container", "ref", elementRef),
                div($map("className", "vg-tracks-top"),
                        div($map("className", "vg-add-tracks"), "Add tracks",
                                a($map("className", "vg-close-track-window", "onClick", cancel), "×")
                        )
                ),
                div($map("className", "vg-tracks-middle"), components),
                div($map("className", "vg-tracks-bottom"),
                        div($map("ref", ADD_BUTTON, "className", "vg-add-selected-tracks", "onClick", doImport), "Add selected"),
                        div($map("className", "vg-cancel-selected-tracks", "onClick", cancel), "Cancel")
                )
        );
    }

    void addButtonIsEnabled(boolean enabled){
        Element el = this.refs.$get(ADD_BUTTON);
        if (enabled){
            el.classList.remove("disabled");
        } else {
            el.classList.add("disabled");
        }
    }

    private void submitUrl(String url) {
        console.log("ENTERED " + url);
        String importedFrom = UIUtils.humanReadableFilename(url);
        TrackData importedTrack = new TrackData(new Track(importedFrom, null), $array());
        importedTrack.track.importedFrom = importedFrom;
        importedTrack.state = TrackData.STATE_IMPORTING;
        state.tracks.push(importedTrack);
        setState(state);

        Media _media = new Media();
        _media.url = url;
        _media.commands = $array(NO_CACHE);
        _media.humanReadableFilename = importedFrom;
        model.addNewMedia(_media)
                .flatMap(_id -> {
                    Media media = model.getMedia(_id);
                    media.commands = $array(NO_CACHE, DOWNLOAD);
                    console.log(media);
                    return model.saveMedias($array(media));
                })
                .flatMap(media1 -> {
                    console.log("Downloading media", media1);
                    return model.getJobUpdates().filter(job -> job.mediaId.equals(media1.id) && job.isFinished);
                })
                .flatMap(job -> {
                    console.log("Download complete", job.mediaId);
                    String downloadUrl = CloudServices.API_1_STORAGE + "/" + job.mediaId + "/download";
                    return Requests.getJson(downloadUrl, ImportedTrack.class);
                })
                .map(it -> TrackImport.convert(it, importedFrom))
                .doOnError(err -> {
                    console.error(err);
                    importedTrack.state = TrackData.STATE_ERROR;
                    setState(state);
                })
                .subscribe(track -> {
                    console.log("Parsed media", track.track.importedFrom);
                    importedTrack.track = track.track;
                    importedTrack.state = TrackData.STATE_IMPORTED;
                    importedTrack.events = track.events;
                    setState(state);
                });
    }

    public Array<ReactElement> tracks(Array<TrackData> tracks) {
        return tracks.map((track, idx, arr) ->  createElement(TrackDataComponent.class, new TrackDataComponent.TrackDataProps(track, props.timeline)));
    }

    public void cancel() {
        hide();
        state = initialState();
        setState(state);
    }

    public void save() {
        state.importCallback.$invoke(state.tracks);
        hide();
        state = initialState();
        setState(state);
    }

    public void show() {
        parent.style.setProperty("display", "block", null);
    }

    public void hide() {
        parent.style.setProperty("display", "none", null);
    }
}
