const express = require('express');
const { authOptional, authRequired } = require('../middleware/auth');
const { chat, streamChat, FAQ } = require('../services/chatService');

const router = express.Router();

router.get('/faq', (_req, res) => {
  res.json({ count: FAQ.length, faq: FAQ });
});

router.get('/faq/:id',(req,res)=>{const f=FAQ.find(x=>x.id===req.params.id);if(!f)return res.status(404).json({error:'Source not found'});res.json(f);});

router.post('/', authOptional, async (req, res) => {
  const { message, session_id, stream } = req.body || {};
  if (!message || !String(message).trim()) {
    return res.status(400).json({ error: 'message required' });
  }

  try {
    if (stream) {
      return streamChat(res, {
        sessionId: session_id,
        userId: req.user?.id,
        message: String(message).trim()
      });
    }
    const result = await chat({
      sessionId: session_id,
      userId: req.user?.id,
      message: String(message).trim()
    });
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message || 'Chat failed' });
  }
});

const proposals=require('../services/orderProposal');
router.post('/proposals',authRequired,(req,res)=>{
 try{res.json(proposals.create(req.user.id,req.body.session_id,req.body.items));}catch(e){res.status(e.status||500).json({error:e.message});}
});
router.post('/proposals/:id/quote',authRequired,async(req,res)=>{
 try{res.json(await proposals.quote(req.params.id,req.user.id,req.body.shippingAddress));}catch(e){res.status(e.status||502).json({error:e.message});}
});
module.exports = router;
