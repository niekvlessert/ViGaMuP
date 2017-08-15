/* emu2149.h */
#ifndef _EMU2149_H_
#define _EMU2149_H_

#include <stdint.h>

#define EMU2149_VOL_DEFAULT 1
#define EMU2149_VOL_YM2149 0
#define EMU2149_VOL_AY_3_8910 1

#define kss_PSG_MASK_CH(x) (1<<(x))

#ifdef __cplusplus
extern "C"
{
#endif

  typedef struct __PSG
  {

    /* Volume Table */
    uint32_t *voltbl;

    uint8_t reg[0x20];
    int32_t out;

    uint32_t clk, rate, base_incr, quality;

    uint32_t count[3];
    uint32_t volume[3];
    uint32_t freq[3];
    uint32_t edge[3];
    uint32_t tmask[3];
    uint32_t nmask[3];
    uint32_t mask;

    uint32_t base_count;

    uint32_t env_volume;
    uint32_t env_ptr;
    uint32_t env_face;

    uint32_t env_continue;
    uint32_t env_attack;
    uint32_t env_alternate;
    uint32_t env_hold;
    uint32_t env_pause;
    uint32_t env_reset;

    uint32_t env_freq;
    uint32_t env_count;

    uint32_t noise_seed;
    uint32_t noise_count;
    uint32_t noise_freq;

    /* rate converter */
    uint32_t realstep;
    uint32_t psgtime;
    uint32_t psgstep;

    /* I/O Ctrl */
    uint32_t adr;

    /* output of channels */
    int16_t ch_out[3];

  } PSG;

  void kss_PSG_set_quality (PSG * psg, uint32_t q);
  void kss_PSG_set_rate (PSG * psg, uint32_t r);
  PSG *kss_PSG_new (uint32_t clk, uint32_t rate);
  void kss_PSG_reset (PSG *);
  void kss_PSG_delete (PSG *);
  void kss_PSG_writeReg (PSG *, uint32_t reg, uint32_t val);
  void kss_PSG_writeIO (PSG * psg, uint32_t adr, uint32_t val);
  uint8_t kss_PSG_readReg (PSG * psg, uint32_t reg);
  uint8_t kss_PSG_readIO (PSG * psg);
  int16_t kss_PSG_calc (PSG *);
  void kss_PSG_setVolumeMode (PSG * psg, int type);
  uint32_t kss_PSG_setMask (PSG *, uint32_t mask);
  uint32_t kss_PSG_toggleMask (PSG *, uint32_t mask);
    
#ifdef __cplusplus
}
#endif

#endif
