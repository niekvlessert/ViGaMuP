#include <jni.h>
#include <string.h>
#include <assert.h>
#include <pthread.h>
#include <unistd.h>
#include <stdio.h>

// for __android_log_print(ANDROID_LOG_INFO, "YourApp", "formatted message");
#include <android/log.h>

// for native audio
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <malloc.h>
#include <tgmath.h>
#include <dirent.h>


#include "libkss/src/kssplay.h"
#include "game-music-emu-0.6.0/gme/gme.h"
#include "vgmplay/src/VGMPlay_Intf.h"
#include "dumb/include/dumb.h"
#include "dumb/include/internal/dumb.h"
#include "dumb/include/internal/it.h"

// engine interfaces
static SLObjectItf engineObject = NULL;
static SLEngineItf engineEngine;

// output mix interfaces
static SLObjectItf outputMixObject = NULL;

// buffer queue player interfaces
static SLObjectItf bqPlayerObject = NULL;
static SLPlayItf bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf bqPlayerBufferQueue;
static SLEffectSendItf bqPlayerEffectSend;
static SLMuteSoloItf bqPlayerMuteSolo;
static SLVolumeItf bqPlayerVolume;
static SLmilliHertz bqPlayerSampleRate = 0;

KSSPLAY *kssplay;
KSS *kss;
FILE *fp;

int secondsToGenerate = 0;
int trackLength = 0;
int initialDataCopied = 0;
int generatingAllowed = 0;
char *currentFile = "";
int queueSecond = 0;
int secondsPlayed = 0;
int isPlaying = 0;
int isPaused = 1;
int isBuffering = 0;
int firstRun = 1;
int16_t *fullTrackWavebuf;
int16_t *wavebuf;
int16_t *wavebuf2;
int nextMessageSend = 0;
int terminateThread = 0;
int threadTerminated = 0;
int loopTrack = 0;
int loopTrackWasEnabled = 0;
int skipToNextTrack = 0;
int secondsToSkipWhenCopyingToBuffer = 2;
int nextTrackStarted = 0;

int globalTrackNumber = 0;
int globalSecondsToPlay = 0;

int slesThingsCreated = 0;

long previousSum = 0;

static int MUSIC_TYPE_KSS = 0;
static int MUSIC_TYPE_SPC = 1;
static int MUSIC_TYPE_VGM = 2;
static int MUSIC_TYPE_NSF = 3;
static int MUSIC_TYPE_TRACKERS = 4;

enum ENDIANNESS { DUMB_LITTLE_ENDIAN = 0, DUMB_BIG_ENDIAN };

typedef struct {
    DUH_SIGRENDERER *renderer;
    DUH *src;
    sample_t **sig_samples;
    long sig_samples_size;
    FILE *dst;
    float delta;
    int bufsize;
    bool is_stdout;
} streamer_t; // Dumb streamer struct

typedef struct {
    int bits;
    int endianness;
    int is_unsigned;
    int freq;
    int quality;
    int n_channels;
    float volume;
    float delay;
    const char *input;
    char *output;
} settings_t;

streamer_t streamer;
settings_t settings;

char *LOG_TAG = "Glue";

int activeGameType = 0;

Music_Emu* emu;

pthread_mutex_t lock;
pthread_t t1;

int deviceSampleRate = 48000;
UINT32 SampleRate = 48000;
extern UINT32 VGMMaxLoop;
float MasterVol = 2.0f;

int16_t *queueBuffer1, *queueBuffer2, *queueBuffer3;
int16_t *queueBufferSilence;
int queueBufferToUse = 1;

// processing callback to handler class
typedef struct tick_context {
    JavaVM  *javaVM;
    jclass   PlayerServiceClz;
    jobject  PlayerServiceObj;
    pthread_mutex_t  lock ;
} TickContext;
TickContext g_ctx;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "JNI onload!");

    JNIEnv* env;
    memset(&g_ctx, 0, sizeof(g_ctx));

    g_ctx.javaVM = vm;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR; // JNI version not supported.
    }

    g_ctx.PlayerServiceObj = NULL;
    return  JNI_VERSION_1_6;
}

// this callback handler is called every time a buffer finishes playing
void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
    queueSecond = 1;
    generatingAllowed = 1;
    //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Callback called!!");
}

int checkForSilence(int16_t* queueBuffer){
    long sum = 0;
    for (int i = 0; i < 10000; i++) {
        sum += queueBuffer[i];
    }
    if (sum != 0 && previousSum != sum) {
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "No silence... %ld, previous was %ld", sum, previousSum);
        previousSum = sum;
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Silence!!");
    previousSum=0;
    skipToNextTrack = 1; // for loop mode
    return 1;
}

int queueSecondIfRequired(void *context){
    /*int i, j;
    int a=1;
    int16_t sum=0;*/
    if (queueSecond && secondsPlayed+1<trackLength && !nextTrackStarted) {
        if (queueBufferToUse == 1) {
            /*for (j = 0; j<secondsPlayed*10; j++) {
                for (i = 0; i < 4800; i++) {
                    a++;
                    sum += fullTrackWavebuf[i];
                }
                __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "memory info at secondsplayed %d, position %d: sum %d", secondsPlayed, j, sum);
            }*/
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "queuing a second! %d", secondsPlayed);
            memcpy(queueBuffer1, &fullTrackWavebuf[secondsPlayed * deviceSampleRate * 2], deviceSampleRate*4);
            memcpy(queueBufferSilence, (int8_t *) &fullTrackWavebuf[secondsPlayed * deviceSampleRate], deviceSampleRate);
            queueSecond = 0;
            queueBufferToUse++;
            secondsPlayed++;
            (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer1, deviceSampleRate*4);
            if (activeGameType==0) {
               if (checkForSilence(queueBufferSilence)) secondsPlayed=trackLength;
            } // force next track for normal playback
        } else {
            if (queueBufferToUse == 2 && queueSecond != 0) {
                __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "queuing a second 2 ! %d", secondsPlayed);
                memcpy(queueBuffer2, &fullTrackWavebuf[secondsPlayed * deviceSampleRate * 2], deviceSampleRate*4);
                //if (checkForSilence(queueBuffer2)) nextTrack(context);
                (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer2, deviceSampleRate*4);
                queueSecond = 0;
                queueBufferToUse++;
                secondsPlayed++;
            } else {
                if (queueBufferToUse == 3 && queueSecond != 0) {
                    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "queuing a second 3! %d", secondsPlayed);
                    memcpy(queueBuffer3, &fullTrackWavebuf[secondsPlayed * deviceSampleRate * 2], deviceSampleRate*4);
                    //if (checkForSilence(queueBuffer3)) nextTrack(context);
                    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer3, deviceSampleRate*4);
                    queueSecond = 0;
                    queueBufferToUse = 1;
                    secondsPlayed++;
                }
            }
        }
        return 1;
    }
    return 0;
}

void* generateAudioThread(void* context){
    TickContext *pctx = (TickContext*) context;
    JavaVM *javaVM = pctx->javaVM;
    JNIEnv *env;
    jint res = (*javaVM)->GetEnv(javaVM, (void**)&env, JNI_VERSION_1_6);
    if (res != JNI_OK) {
        res = (*javaVM)->AttachCurrentThread(javaVM, &env, NULL);
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Attaching!!");
        if (JNI_OK != res) {
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Failed to AttachCurrentThread, ErrorCode = %d", res);
        }
    }
    jmethodID nextTrackId = (*env)->GetMethodID(env, pctx->PlayerServiceClz, "nextTrack", "()V");
    jmethodID setBufferBarProgressId = (*env)->GetMethodID(env, pctx->PlayerServiceClz, "setBufferBarProgress", "()V");
    jmethodID setSeekBarThumbProgressId = (*env)->GetMethodID(env, pctx->PlayerServiceClz, "setSeekBarThumbProgress", "()V");
    //pthread_t id = pthread_self();
    //int a=0;
    int queueSecondResult=0;
    while(1)
    {
        if (!nextTrackStarted){
            if (!loopTrack || activeGameType == MUSIC_TYPE_SPC) {

                if (loopTrackWasEnabled) { //fade to next track...
                    initialDataCopied = 1;
                    secondsToGenerate = 2;
                    generatingAllowed = 1;
                    loopTrackWasEnabled = 0;
                    terminateThread = 0;
                    secondsPlayed = trackLength - 2;
                }

                isBuffering = 1;

                while (secondsToGenerate > 0 && generatingAllowed == 1 && isPlaying &&
                       secondsPlayed != trackLength && !terminateThread) {
                    /*if (!initialDataCopied) {
                        initialDataCopied = 1;
                        memcpy(fullTrackWavebuf, wavebuf, deviceSampleRate*2);
                        memcpy(&fullTrackWavebuf[deviceSampleRate*2], wavebuf2, deviceSampleRate*2*2);
                        secondsPlayed = 2;
                        if (secondsToGenerate < secondsToSkipWhenCopyingToBuffer) secondsToGenerate = 0;
                        else secondsToGenerate = secondsToGenerate - secondsToSkipWhenCopyingToBuffer;
                        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "in copy initial data, secondsToGenerate: %d", secondsToGenerate);

                    }*/
                    if (secondsToGenerate>0) {
                        if (activeGameType == MUSIC_TYPE_KSS) KSSPLAY_calc(kssplay, &fullTrackWavebuf[(trackLength - secondsToGenerate) * deviceSampleRate * 2],deviceSampleRate);

                        if (activeGameType == MUSIC_TYPE_SPC || activeGameType == MUSIC_TYPE_NSF) {
                            pthread_mutex_lock(&lock);
                            gme_play(emu, deviceSampleRate * 2,&fullTrackWavebuf[(trackLength - secondsToGenerate) * deviceSampleRate * 2]);
                            pthread_mutex_unlock(&lock);
                        }

                        if (activeGameType == MUSIC_TYPE_VGM) {
                            pthread_mutex_lock(&lock);
                            FillBuffer(&fullTrackWavebuf[(trackLength - secondsToGenerate) *deviceSampleRate * 2], deviceSampleRate);
                            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Creating vgm data...");
                            pthread_mutex_unlock(&lock);
                        }

                        if (activeGameType == MUSIC_TYPE_TRACKERS) {
                            pthread_mutex_lock(&lock);
                            //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Creating tracker data... secondsToGenerate: %i, trackLength: %i ", secondsToGenerate, trackLength);
                            duh_render_int(streamer.renderer, &streamer.sig_samples,&streamer.sig_samples_size, settings.bits, settings.is_unsigned, settings.volume,streamer.delta, deviceSampleRate, &fullTrackWavebuf[(trackLength - secondsToGenerate) * deviceSampleRate * 2]);
                            //duh_render_int(streamer.renderer, &streamer.sig_samples,&streamer.sig_samples_size, settings.bits, settings.is_unsigned, settings.volume,streamer.delta, deviceSampleRate, wavebuf);
                            pthread_mutex_unlock(&lock);
                        }
                    }

                    (*env)->CallVoidMethod(env, pctx->PlayerServiceObj, setBufferBarProgressId);
                    secondsToGenerate--;
                    if (secondsToGenerate == 3) {
                        if (activeGameType == MUSIC_TYPE_KSS)
                            KSSPLAY_fade_start(kssplay, 1000); //todo: only fade when song loops...
                    }

                    queueSecondResult = queueSecondIfRequired(context);

                    if (queueSecondResult == 1)
                        (*env)->CallVoidMethod(env, pctx->PlayerServiceObj, setSeekBarThumbProgressId);
                    nextMessageSend = 0;
                }
                if (isPlaying) {
                    if (queueSecondIfRequired(context) == 1)
                        (*env)->CallVoidMethod(env, pctx->PlayerServiceObj, setSeekBarThumbProgressId);
                }
                isBuffering = 0;
               // __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "next maybe?! isPlaying %d nextMessageSend %d SecondsPlayed %d tracklength %d nexttrackid %d", isPlaying,nextMessageSend, secondsPlayed, trackLength, nextTrackId);
                if (isPlaying && !nextMessageSend && secondsPlayed +1 == trackLength && trackLength != 0) {
                    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "yep, next!!!");
                    sleep(1);
                    (*env)->CallVoidMethod(env, pctx->PlayerServiceObj, nextTrackId);

                    nextMessageSend = 1;
                    //(*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
                    //(*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
                }
            } else {
                if (isPlaying) {
                    while (loopTrack) {
                        loopTrackWasEnabled = 1;
                        if (queueSecond) {
                            queueSecond = 0;
                            if (skipToNextTrack) {
                                __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Skip to next called...");
                                skipToNextTrack = 0;
                                (*env)->CallVoidMethod(env, pctx->PlayerServiceObj, nextTrackId);
                                break;
                            }
                            if (queueBufferToUse == 1) {
                                if (activeGameType == MUSIC_TYPE_KSS)
                                    KSSPLAY_calc(kssplay, queueBuffer1, deviceSampleRate);
                                if (activeGameType == MUSIC_TYPE_VGM)
                                    FillBuffer(queueBuffer1, deviceSampleRate);
                                (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer1,
                                                                deviceSampleRate * 4);
                                queueBufferToUse++;
                                checkForSilence(queueBuffer1);
                            } else {
                                if (queueBufferToUse == 2) {
                                    if (activeGameType == MUSIC_TYPE_KSS)
                                        KSSPLAY_calc(kssplay, queueBuffer2, deviceSampleRate);
                                    if (activeGameType == MUSIC_TYPE_VGM)
                                        FillBuffer(queueBuffer2, deviceSampleRate);
                                    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer2,
                                                                    deviceSampleRate * 4);
                                    queueBufferToUse++;
                                } else {
                                    if (activeGameType == MUSIC_TYPE_KSS)
                                        KSSPLAY_calc(kssplay, queueBuffer3, deviceSampleRate);
                                    if (activeGameType == MUSIC_TYPE_VGM)
                                        FillBuffer(queueBuffer3, deviceSampleRate);
                                    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, queueBuffer3,
                                                                    deviceSampleRate * 4);
                                    queueBufferToUse = 1;
                                }
                            }
                        }
                    }
                }
            }
        }
        usleep (500);
        /*if (a==10000) {
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "In thread... isplaying: %d secondstogenerate: %d generatingallowed: %d secondsplayed: %d tracklength: %d terminatethread: %d", isPlaying, secondsToGenerate, generatingAllowed,
                                secondsPlayed, trackLength, terminateThread);
            a=0;
        }
        a++;*/
        if (terminateThread==1) {
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "terminating thread");
            (*javaVM)->DetachCurrentThread(javaVM);
            threadTerminated=1;
            pthread_exit(NULL);
        }
    }
}

void Java_nl_vlessert_vigamup_PlayerService_setProgress(JNIEnv* env, jclass clazz, int progress){
    pthread_mutex_lock(&lock);
    secondsPlayed=progress;
    pthread_mutex_unlock(&lock);
}

jboolean Java_nl_vlessert_vigamup_PlayerService_togglePlayback(JNIEnv* env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Paused status!!: %d", isPaused);
    if (isPaused) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
        isPaused = 0;
        return 0;
    } else {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
        isPaused = 1;
        return 1;
    }
}

void Java_nl_vlessert_vigamup_PlayerService_pausePlayback(JNIEnv* env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Pause playback");
    if (!isPaused) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PAUSED);
        isPaused = 1;
    }
}

void Java_nl_vlessert_vigamup_PlayerService_resumePlayback(JNIEnv* env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Resume playback");
    if (isPaused) {
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
        isPaused = 0;
    }
}

jboolean Java_nl_vlessert_vigamup_PlayerService_toggleLoopTrack(JNIEnv* env, jclass clazz) {
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Pause status!!: %d", isPaused);
    if (loopTrack) {
        loopTrack = 0;
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Looping uit!!");
        return 0;
    } else {
        loopTrack = 1;
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Looping aan!!");
        return 1;
    }
}

void handle_error( const char* str ) {
    if (str) {
        __android_log_print(ANDROID_LOG_INFO, "vigamup player service:", "Error: %s\n", str);
        getchar();
    }
}

void Java_nl_vlessert_vigamup_PlayerService_playTrack(JNIEnv* env, jclass clazz, int musicType, char *file, int trackNr, int secondsToPlay) {

    const char *utf8File = (*env)->GetStringUTFChars(env, file, NULL);

    nextTrackStarted = 0;

    free(wavebuf);
    free(wavebuf2);
    wavebuf = malloc(deviceSampleRate * 2 * sizeof(int16_t));
    wavebuf2 = malloc(deviceSampleRate * 2 * sizeof(int16_t));

    secondsToSkipWhenCopyingToBuffer = 2;

    bool run;
    char *buffer;
    int read_samples;
    int read_bytes;


    if (isPlaying || firstRun) {
        firstRun = 0;
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Ok, still playing...");

        isPlaying=0;
        //if (isBuffering==1) secondsToSkipWhenCopyingToBuffer = 1; // ugly fix for weird queue problem... probably some timing issue, can't find it...
        while (isBuffering){
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Still buffering...");
            usleep(100);
        }

        //pthread_mutex_lock(&lock);

        SLresult result = (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);
        if (SL_RESULT_SUCCESS != result) {
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Clear failed, result=%d\n", result);
            return;
        }

        switch (musicType) {
            case 0:
                if (strcmp(utf8File,currentFile)!=0) {
                    if (strcmp("", currentFile) != 0) {
                        KSSPLAY_delete(kssplay);
                        KSS_delete(kss);
                    }
                    //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Loading KSS... %s", utf8File);

                    if ((kss = KSS_load_file(utf8File)) == NULL) {
                        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Error loading... KSS...");
                        return;
                    }
                    kssplay = KSSPLAY_new(deviceSampleRate, 2, 16); // so frequency (48k) * channels (1) * bitrate (16) = 96000 bytes per second
                    KSSPLAY_set_device_quality(kssplay, EDSC_PSG, 1);
                    KSSPLAY_set_device_quality(kssplay, EDSC_SCC, 1);
                    KSSPLAY_set_device_quality(kssplay, EDSC_OPLL, 1);
                    KSSPLAY_set_data(kssplay, kss);
                    KSSPLAY_set_master_volume(kssplay, 80); //on 100 it will overpower when increasing OPLL volume
                    KSSPLAY_set_device_volume(kssplay, EDSC_PSG, 20); //emphasise drums (mostly)
                    KSSPLAY_set_device_volume(kssplay, EDSC_OPLL, 30); //FMPAC is just to soft by default imho
                    currentFile = utf8File;
                }

                //(*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
            case 1:
            case 3:
                gme_delete( emu );
                emu = NULL;
                break;
            case 2:
                StopVGM();
                __android_log_print(ANDROID_LOG_INFO, "VGM", "Stop playback..");
                CloseVGMFile();
                VGMPlay_Deinit();
                break;
            case 4:
                memset(&streamer, 0, sizeof(streamer_t));
                memset(&settings, 0, sizeof(settings_t));

                settings.freq = deviceSampleRate;
                settings.n_channels = 2;
                settings.bits = 16;
                settings.endianness = DUMB_LITTLE_ENDIAN;
                settings.is_unsigned = false;
                settings.volume = 1.0f;
                settings.delay = 0.0f;
                settings.quality = DUMB_RQ_CUBIC;

                __android_log_print(ANDROID_LOG_INFO, "PlayerEngine", "Loading %s", utf8File);

                memset(&streamer, 0, sizeof(streamer_t));
                dumb_register_stdfiles();
                streamer.src = dumb_load_any(utf8File, 0, 0);
                streamer.renderer =
                        duh_start_sigrenderer(streamer.src, 0, settings.n_channels, 0);
                streamer.delta = 65536.0f / deviceSampleRate;
                streamer.bufsize = 4096 * (settings.bits / 8) * settings.n_channels;

        }

        //pthread_mutex_unlock(&lock);

        if (fullTrackWavebuf!=NULL) free(fullTrackWavebuf);
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Free werkt ook nog... %s", utf8File);
    }

    initialDataCopied = 0;
    generatingAllowed = 0;
    queueSecond = 0;
    secondsPlayed = 0;
    secondsToGenerate = secondsToPlay;
    trackLength = secondsToPlay;
    skipToNextTrack = 0;
    activeGameType = musicType;
    fullTrackWavebuf = malloc(deviceSampleRate * 4 * secondsToPlay);
    globalSecondsToPlay = secondsToPlay;

    globalTrackNumber = trackNr;

    /*if (!isPaused || nextMessageSend) {
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Oe activating playing again... %s", utf8File);
        pthread_mutex_lock(&lock);
        (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
        nextMessageSend = 0;
        pthread_mutex_unlock(&lock);
    }*/

    if (musicType == MUSIC_TYPE_SPC) trackNr = 0; // since nsf requires track numbers and spc must be 0 and same player engine is used, reset trackNr...

    switch (musicType) {
        case 0:
            //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Gaat volgende track zetten in... %s", utf8File);

            KSSPLAY_reset(kssplay, globalTrackNumber, 0);
            secondsToGenerate = globalSecondsToPlay;
            if (globalSecondsToPlay==1) globalSecondsToPlay=3;
            trackLength = globalSecondsToPlay;

            //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Gaat calculaten... %s", utf8File);
            KSSPLAY_calc(kssplay, wavebuf, deviceSampleRate);
            KSSPLAY_calc(kssplay, wavebuf2, deviceSampleRate);
            break;
        case 1:
        case 3:
            handle_error(gme_open_file(utf8File, &emu, deviceSampleRate ));
            __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "File opened!!\n");

            handle_error(gme_start_track( emu, trackNr));
            if (secondsToPlay>20) {
                gme_set_fade(emu, (secondsToPlay - 5) * 1000);
            }

            gme_play(emu, deviceSampleRate*2, wavebuf);
            gme_play(emu, deviceSampleRate*2, wavebuf2);

            break;

        case 2:
            VGMPlay_Init();
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_Glue", "Loop track: %d\n",loopTrack);
            if (loopTrack) VGMMaxLoop = 0x00;
            VGMPlay_Init2();
            if (!OpenVGMFile(utf8File)){
                __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_Glue", "File error!!\n");

            };
            PlayVGM();

            FillBuffer(wavebuf, deviceSampleRate);
            if (trackLength>1) FillBuffer(wavebuf2, deviceSampleRate);

            break;

        case 4:
            run = true;
            //trackLength=round(duh_get_length(streamer.src)/65535);
            trackLength=secondsToPlay;
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_Glue", "Track length %d\n", trackLength);
            //*buffer = malloc(streamer.bufsize);

            duh_render_int(streamer.renderer, &streamer.sig_samples,&streamer.sig_samples_size, settings.bits, settings.is_unsigned, settings.volume,streamer.delta, deviceSampleRate, wavebuf);
            duh_render_int(streamer.renderer, &streamer.sig_samples,&streamer.sig_samples_size, settings.bits, settings.is_unsigned, settings.volume,streamer.delta, deviceSampleRate, wavebuf2);
    }

    (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, wavebuf, deviceSampleRate*4); //16 bit = 2 bytes, 2 channels = 2x, so *4
    if (trackLength>1) (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, wavebuf2, deviceSampleRate*4);

    nextTrackStarted = 0;

    isPlaying = 1;
}

// create the engine and output mix objects
void Java_nl_vlessert_vigamup_PlayerService_createEngine(JNIEnv* env, jobject instance)
{
    SLresult result;

    if (!slesThingsCreated) {

        // create engine
        result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // realize the engine
        result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // get the engine interface, which is needed in order to create other objects
        result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // create output mix, with environmental reverb specified as a non-required interface
        //const SLInterfaceID ids[1] = {SL_IID_ENVIRONMENTALREVERB};
        //const SLInterfaceID ids[1] = {SL_IID_VOLUME};
        const SLboolean req[1] = {SL_BOOLEAN_FALSE};
        result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, req);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // realize the output mix
        result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

    }
    pthread_attr_t  threadAttr_;

    pthread_attr_init(&threadAttr_);
    pthread_attr_setdetachstate(&threadAttr_, PTHREAD_CREATE_DETACHED);

    pthread_mutex_init(&g_ctx.lock, NULL);

    jclass clz = (*env)->GetObjectClass(env, instance);
    g_ctx.PlayerServiceClz = (*env)->NewGlobalRef(env, clz);
    g_ctx.PlayerServiceObj = (*env)->NewGlobalRef(env, instance);

    pthread_create(&t1,NULL,generateAudioThread,&g_ctx);

    /*jclass cls = (*env)->GetObjectClass(env, instance);

    jmethodID callbackMethod = (*env)->GetMethodID(env, cls, "nextTrack", "()V");

    (*env)->CallVoidMethod(env, instance, callbackMethod);*/

    //pthread_join(t1,NULL);

    queueBuffer1 = malloc(deviceSampleRate*4);
    queueBuffer2 = malloc(deviceSampleRate*4);
    queueBuffer3 = malloc(deviceSampleRate*4);
    queueBufferSilence = malloc(deviceSampleRate);
    fullTrackWavebuf = NULL;

    wavebuf = malloc(deviceSampleRate * 2 * sizeof(int16_t));
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "wavebuf size: %d", deviceSampleRate * 2 * sizeof(int16_t));
    wavebuf2 = malloc(deviceSampleRate * 2 * sizeof(int16_t));
}

// create buffer queue audio player
void Java_nl_vlessert_vigamup_PlayerService_createBufferQueueAudioPlayer(JNIEnv* env,
                                                                           jclass clazz, jint sampleRate, jint bufSize) {

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Creating the stuff...");

    deviceSampleRate = sampleRate;

    SLresult result;

    if (!slesThingsCreated) {
        if (sampleRate >= 0 && bufSize >= 0) {
            bqPlayerSampleRate = sampleRate * 1000;
            /*
             * device native buffer size is another factor to minimize audio latency, not used in this
             * sample: we only play one giant buffer here
             */
            //bqPlayerBufSize = bufSize;
        }

        // configure audio source
        SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                           2};
        /*SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 1, SL_SAMPLINGRATE_8,
                                       SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                       SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};*/ //8 bit, 1

        SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
                                       SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
                                       SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN};
        /*
         * Enable Fast Audio when possible:  once we set the same rate to be the native, fast audio path
         * will be triggered
         */
        if (bqPlayerSampleRate) {
            format_pcm.samplesPerSec = bqPlayerSampleRate;       //sample rate in mili second
        }
        SLDataSource audioSrc = {&loc_bufq, &format_pcm};

        // configure audio sink
        SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
        SLDataSink audioSnk = {&loc_outmix, NULL};

        /*
         * create audio player:
         *     fast audio does not support when SL_IID_EFFECTSEND is required, skip it
         *     for fast audio case
         */
        const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME, SL_IID_EFFECTSEND,
                /*SL_IID_MUTESOLO,*/};
        const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
                /*SL_BOOLEAN_TRUE,*/ };

        result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc,
                                                    &audioSnk,
                                                    bqPlayerSampleRate ? 2 : 3, ids, req);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // realize the player
        result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // get the play interface
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // get the buffer queue interface
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                                 &bqPlayerBufferQueue);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // register callback on the buffer queue
        result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback,
                                                          NULL);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // get the effect send interface
        bqPlayerEffectSend = NULL;
        if (0 == bqPlayerSampleRate) {
            result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_EFFECTSEND,
                                                     &bqPlayerEffectSend);
            assert(SL_RESULT_SUCCESS == result);
            (void) result;
        }

#if 0   // mute/solo is not supported for sources that are known to be mono, as this is
        // get the mute/solo interface
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_MUTESOLO, &bqPlayerMuteSolo);
        assert(SL_RESULT_SUCCESS == result);
        (void)result;
#endif

        // get the volume interface
        result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        // set the player's state to playing
        result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
        assert(SL_RESULT_SUCCESS == result);
        (void) result;

        slesThingsCreated = 1;
    }
}

// shut down the native audio system
void Java_nl_vlessert_vigamup_PlayerService_shutdown(JNIEnv* env, jclass clazz)
{
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown!!!");

    //(*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
    //SLresult result = (*bqPlayerBufferQueue)->Clear(bqPlayerBufferQueue);

    // destroy buffer queue audio player object, and invalidate all associated interfaces
    if (bqPlayerObject != NULL) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
        bqPlayerEffectSend = NULL;
        bqPlayerMuteSolo = NULL;
        bqPlayerVolume = NULL;
    }

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown2!!!");

    // destroy output mix object, and invalidate all associated interfaces
    if (outputMixObject != NULL) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown3!!!");

    // destroy engine object, and invalidate all associated interfaces
    if (engineObject != NULL) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown4!!!");

    terminateThread = 1;
    while (!threadTerminated) {
        usleep(100);
    }

    free (fullTrackWavebuf);
    free (wavebuf);
    free (wavebuf2);
    free (queueBuffer1);
    free (queueBuffer2);
    free (queueBuffer3);
    free (queueBufferSilence);
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown5!!!");

    KSSPLAY_delete(kssplay);
    KSS_delete(kss);
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown6!!!");

    secondsToGenerate = 0;
    trackLength = 0;
    initialDataCopied = 0;
    generatingAllowed = 0;
    currentFile = "";
    queueSecond = 0;
    secondsPlayed = 0;
    isPlaying = 0;
    isPaused = 1;
    isBuffering = 0;
    nextMessageSend = 0;
    terminateThread = 0;
    threadTerminated = 0;
    loopTrack = 0;

    previousSum = 0;


    pthread_mutex_destroy(&lock);
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Shutdown7!!!");

}

void investigateTrackFurther(int tracknr){
    int16_t *generateWavebuf  = malloc (96000*600); //10 minutes seems enough to detect loops and for every other song to end....
    int16_t *tempWavebuf = malloc(96000);
    int secondsCalculated = 0;
    char fn[101];
    FILE *f;

    KSSPLAY_reset(kssplay, tracknr, 0);
    // loop detection: if second >1 compare with first second, if same loop detected
    // non loop song: detect silence
    while (1){
        KSSPLAY_calc(kssplay, tempWavebuf, 48000);
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Calculated %d...\n", secondsCalculated);
        if (secondsCalculated>0){
            if (memcmp(generateWavebuf, tempWavebuf, 44100)==0) {
                __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Loop detected in %d on second %d!!\n", tracknr, secondsCalculated);
                break;
            }
        }
        memcpy((int8_t *) &generateWavebuf[secondsCalculated * 48000], tempWavebuf, 96000);
        snprintf(fn, 100, "/sdcard/Download/%d.txt", secondsCalculated);
        f =  fopen(fn, "wb");
        fwrite (tempWavebuf, 1, 96000, f);
        fclose(f);
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Calculated %d...\n", secondsCalculated);
        secondsCalculated++;
        if (secondsCalculated==50) break;
    }
    f = fopen("/sdcard/Download/1.wav", "wb");
    fwrite (generateWavebuf, 1, 50*96000, f);
    fclose (f);
}

// some function to generate music track by detecting silence in a kss track... doesn't work well, what about sound effects, noise or double tracks...
void Java_nl_vlessert_vigamup_PlayerService_generateTrackInformation(JNIEnv* env, jclass clazz){
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Generating trackinformation... \n");
    int16_t *tempWavebuf;
    int16_t *tempWavebuf2;
    int16_t *tempWavebuf3;
    int printedBefore = 0;
    //for (int i = 0; i<=255; i++){
        tempWavebuf = malloc (96000); // 1 second
        tempWavebuf2 = malloc (96000); // 1 second
        tempWavebuf3 = malloc (96000); // 1 second
        int i = 72;
        KSSPLAY_reset(kssplay, i, 0);
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Checking track %d\n", i);
        KSSPLAY_calc(kssplay, tempWavebuf, 48000);
        int sum = 0, j = 0;
        for (j = 0; j < 44100; ++j) {
            sum += tempWavebuf[j];
        }
        if (sum != 0) {
            //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Sound detected in track %d (%d)\n", i, sum);
            KSSPLAY_calc(kssplay, tempWavebuf2, 48000);
            if (memcmp(tempWavebuf, tempWavebuf2, 96000)!=0){
                //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Sound found in track %d; second 1 == second 2, probably sfx...\n", i);
            //} else {
                KSSPLAY_calc(kssplay, tempWavebuf3, 48000);
                KSSPLAY_calc(kssplay, tempWavebuf3, 48000);
                KSSPLAY_calc(kssplay, tempWavebuf3, 48000);
                //FILE *f =  fopen("/sdcard/Download/text.txt", "wb");
                //fwrite (tempWavebuf3, 1, 96000, f);
                //fclose(f);
                sum = 0;
                for (j = 0; j < 44100; ++j) { //understand it's 16 bit int!!
                    sum += tempWavebuf3[j];
                    /*if (j == 1000 || j == 44099){
                        //if (!printedBefore) {
                            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "add, waarde %d positie %d\n", sum, j);
                            //printedBefore=1;
                        //}
                    }*/
                }
                if (sum != 0) {
                    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Sound found in track %d, sum of generated 5th second = %d; should be music!!...\n", i, sum);
                    investigateTrackFurther(i);
                }
            }
        //} else {
            //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "Empty track... skipping %d\n",i);
        }
        free (tempWavebuf);
        free (tempWavebuf2);
        free (tempWavebuf3);
    //}
}

jbyteArray Java_nl_vlessert_vigamup_PlayerService_generateSpcTrackInformation(JNIEnv* env, jclass clazz, char *file) {
    const char *utf8File = (*env)->GetStringUTFChars(env, file, NULL);
    char fullPath[1024];
    char spcInfoString[1024];
    char length[5];
    char loopLength[5];
    int doThisTrack = 0;
    jbyteArray Array;
    Array = (*env)->NewByteArray(env, 1024);

    strcpy (fullPath, "/sdcard/Download/ViGaMuP/tmp/");
    strcat (fullPath, utf8File);
    //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "fullpath: %s",fullPath);

    handle_error( gme_open_file(fullPath, &emu, gme_info_only ) );
    gme_info_t* info;
    handle_error( gme_track_info( emu, &info, 0 ) );

    strcpy (spcInfoString, info->song);
    strcat (spcInfoString, ";");
    strcat (spcInfoString, info->game);
    strcat (spcInfoString, ";");
    strcat (spcInfoString, info->author);
    strcat (spcInfoString, ";");
    strcat (spcInfoString, info->copyright);
    strcat (spcInfoString, ";");

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "length: %ld, intro_length: %ld, loop_length: %ld",(long)info->length,(long)info->intro_length,(long)info->loop_length);
    sprintf (length, "%ld", (long) info->length/1000);
    strcat (spcInfoString, length);
    strcat (spcInfoString, ";");
    sprintf (loopLength, "%ld", (long) info->loop_length/1000);
    strcat (spcInfoString, loopLength);
    if ((info->length/1000)>4) doThisTrack = 1; else doThisTrack = 0;
    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "length: %d",doThisTrack);

    gme_free_info( info );
    (*env)->ReleaseStringUTFChars(env, file, utf8File);
    if (!doThisTrack) {
        Array = (*env)->NewByteArray(env, 1);
        (*env)->SetByteArrayRegion(env, Array, 0, 1, (jbyte*)" ");
        return Array;
    } else {
        //return (*env)->NewStringUTF(env, spcInfoString);
        //return (*env)->NewString(env, spcInfoString, 1024);
        Array = (*env)->NewByteArray(env, strlen(spcInfoString));
        (*env)->SetByteArrayRegion(env, Array, 0, strlen(spcInfoString), (jbyte*)spcInfoString);
        return Array;
    }
    //free (fullPath);
    //free (spcInfoString);
}

static int loop_count = 1;


static int loop_callback(void *data) {
    (void)data;
    return --loop_count <= 0;
}



jbyteArray Java_nl_vlessert_vigamup_PlayerService_generateTrackerTrackInformation(JNIEnv* env, jclass clazz, char *path) {

    const char *utf8File = (*env)->GetStringUTFChars(env, path, NULL);

    char fullPath[1024];
    char fullPathAndFileName[1024];

    char trackerInfoString[4096];
    long tracker_length;
    char length[5];
    //const char *tracker_title;

    DIR* FD;
    struct dirent* in_file;

    memset(&streamer, 0, sizeof(streamer_t));
    memset(&settings, 0, sizeof(settings_t));

    settings.freq = deviceSampleRate;
    settings.n_channels = 2;
    settings.bits = 16;
    settings.endianness = DUMB_LITTLE_ENDIAN;
    settings.is_unsigned = false;
    settings.volume = 1.0f;
    settings.delay = 0.0f;
    settings.quality = DUMB_RQ_CUBIC;

    jbyteArray Array;
    Array = (*env)->NewByteArray(env, 32768);

    int i;
    // for all supported files in the directory
      // fetch length
      // fetch title
      // put info in the array: track_nr, name, length,,,filename
    // write to file

    strcpy (fullPath, "/sdcard/Download/ViGaMuP/");
    strcat (fullPath, utf8File);
    strcat (fullPath, "/");

    dumb_register_stdfiles();

    __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "fullpath: %s",fullPath);

    if (NULL == (FD = opendir (fullPath)))
    {
        fprintf(stderr, "Error : Failed to open input directory\n");

        return 1; // todo: return empty array
    }

    strcpy (trackerInfoString,"");
    while ((in_file = readdir(FD))) {

        if (!strcmp(in_file->d_name, "."))
            continue;
        if (!strcmp(in_file->d_name, ".."))
            continue;
        memset(&streamer, 0, sizeof(streamer_t));
        //strcpy(fullPathAndFileName, "/sdcard/Download/ViGaMuP/");
        //strcat(fullPathAndFileName, utf8File);
        //strcat(fullPathAndFileName, "/");
        strcpy(fullPathAndFileName, fullPath);
        strcat(fullPathAndFileName, in_file->d_name);
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "file to open...: %s",
                            fullPathAndFileName);

        streamer.src = dumb_load_any(fullPathAndFileName, 0, 0);

        if (!streamer.src) {
            fprintf(stderr, "Unable to load file %s for playback!\n", in_file->d_name);
        }
        //strcat(trackerInfoString, in_file->d_name);
        //strcat(trackerInfoString, ",,,");

        tracker_length = duh_get_length(streamer.src) / 65536;
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "tracker_length...: %ld",
                            tracker_length);
        sprintf (length, "%ld", (long) tracker_length);
        strcat (trackerInfoString, length);

        /*tracker_title = duh_get_tag(streamer.src, "TITLE");
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "title...: %s",
                            tracker_title);*/

        strcat (trackerInfoString, ";");
        unload_duh(streamer.src);

        //streamer.renderer = duh_start_sigrenderer(streamer.src, 0, settings.n_channels, 0);
        //DUMB_IT_SIGRENDERER *itsr = duh_get_it_sigrenderer(streamer.renderer);
        //dumb_it_set_loop_callback(itsr, loop_callback, NULL);
        //xdumb_it_set_xm_speed_zero_callback(itsr, &dumb_it_callback_terminate, NULL);

        //amount_of_tags = duh_get_tag_iterator_size(streamer.src);
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "amount of tags...: %i",
         //                   amount_of_tags);

        /*for (i = 0; i < streamer.src->n_tags; i++){
            __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "tag %i...: %s",
                                i, streamer.src->tag[i][1]);;
         }

        sigdata = duh_get_it_sigdata(streamer.src);
        __android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "message...: %s", dumb_it_sd_get_sample_name(sigdata, 0));
*/


        //sprintf (length, "%ld", (long) tracker_length);
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "length...: %ld",length);
        //strcat (trackerInfoString, tracker_length);
        //__android_log_print(ANDROID_LOG_INFO, "ViGaMuP_player_engine", "lengths...: %s",trackerInfoString);

    }
    Array = (*env)->NewByteArray(env, strlen(trackerInfoString));
    (*env)->SetByteArrayRegion(env, Array, 0, strlen(trackerInfoString), (jbyte*)trackerInfoString);
    return Array;
    }

