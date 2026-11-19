<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" import="java.util.*,blog.Security" %>
<%!
 String e(Object o){return Security.escape(o);}
 @SuppressWarnings("unchecked") Map<String,Object> m(Object o){return o==null?new HashMap<>(): (Map<String,Object>)o;}
 @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Object o){return o==null?List.of():(List<Map<String,Object>>)o;}
 String date(Object o){return o==null?"":o.toString().substring(0,Math.min(16,o.toString().length()));}
 String excerpt(Object o){String s=Objects.toString(o,"");return s.length()>170?s.substring(0,170)+"…":s;}
%>
<% var me=m(request.getAttribute("me"));String role=Objects.toString(me.get("role"),"");boolean signed=!me.isEmpty(),admin=role.equals("ADMIN"),staff=signed&&!role.equals("USER");String csrf=e(request.getAttribute("csrf"));String current=Objects.toString(request.getAttribute("page"),"forums");var forum=m(request.getAttribute("forum"));String pageTitle=switch(current){case "forums"->"The conversation starts here.";case "forum"->Objects.toString(forum.get("title"),"Forum");case "dashboard"->"Your community, at a glance.";case "moderation"->"Make room for better conversations.";case "users"->"People make the community.";case "edit-forum"->forum.isEmpty()?"Start a new conversation.":"Edit this conversation.";case "profile"->"A little more about you.";case "dictionary"->"The words behind the filter.";case "audit"->"A clear record of every action.";case "verifier"->"Add a trusted pair of eyes.";case "about"->"Thoughtful moderation. Clear rules.";case "contact"->"Need a hand?";case "error"->"Let’s get you back on track.";case "register"->"Join the conversation.";default->"Good to see you again.";}; %>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>
<%=e(pageTitle)%> · Spam Blocker</title>
<link rel="stylesheet" href="/assets/style.css">
<script src="/assets/app.js" defer>
</script>
</head>
<body>
<a class="skip" href="#main">Skip to content</a>
<header class="topbar">
<div class="nav-wrap">
<a href="/app/forums" class="brand">
<span class="brand-icon">s<span>·</span>
</span>
<span>spam<span class="brand-light">blocker</span>
<small>SPACE FOR BETTER CONVERSATIONS</small>
</span>
</a>
<nav aria-label="Main navigation">
<a class="<%=current.equals("forums")?"active":""%>" href="/app/forums">Explore forums</a>
<%if(signed){%>
<a class="<%=current.equals("dashboard")?"active":""%>" href="/app/dashboard">Dashboard</a>
<%if(staff){%>
<a class="<%=current.equals("moderation")?"active":""%>" href="/app/moderation">Moderation</a>
<a class="<%=current.equals("users")?"active":""%>" href="/app/users">People</a>
<%}%>
<%}else{%>
<a href="/app/about">How it works</a>
<%}%>
</nav>
<div class="account-nav">
<%if(signed){%>
<a class="identity" href="/app/profile">
<span class="avatar">
<%=e(me.get("username").toString().substring(0,1).toUpperCase(Locale.ROOT))%>
</span>
<span>
<%=e(me.get("username"))%>
<small>
<%=e(role.toLowerCase(Locale.ROOT))%>
</small>
</span>
</a>
<form method="post" action="/app/logout">
<input type="hidden" name="csrf" value="<%=csrf%>">
<button class="text-button" type="submit">Sign out</button>
</form>
<%}else{%>
<a href="/app/login">Sign in</a>
<a class="button small" href="/app/register">Join the community <span>↗</span>
</a>
<%}%>
</div>
</div>
</header>
<main id="main" class="container">
<%if(staff){%>
<div class="staff-nav">
<span class="eyebrow">
<%=admin?"ADMIN WORKSPACE":"VERIFIER WORKSPACE"%>
</span>
<div>
<%if(admin){%>
<a href="/app/edit-forum">+ New forum</a>
<a href="/app/verifier">Add verifier</a>
<%}%>
<a href="/app/dictionary">Word dictionary</a>
<a href="/app/audit">Activity log</a>
</div>
</div>
<%}%>
<%if(request.getAttribute("flash")!=null){%>
<div class="notice" role="status">
<span>✓</span>
<%=e(request.getAttribute("flash"))%>
</div>
<%}%>
<%if(current.equals("forums")){%>
<section class="hero">
<div>
<p class="eyebrow">
<span class="live-dot">
</span> A LITTLE LESS NOISE. A LOT MORE IDEAS.</p>
<h1>The conversation<br>starts <em>here.</em>
</h1>
<p class="lede">A welcoming place to share, learn, and connect.<br>Thoughtful discussions, with a little help from moderation.</p>
<div class="hero-links">
<a class="button" href="#discussions">Find your conversation <span>↓</span>
</a>
<a class="quiet-link" href="/app/about">See how it works ↗</a>
</div>
</div>
<div class="hero-art" aria-label="A helpful comment passes the filter; a flagged comment waits for review">
<div class="orbit">
</div>
<div class="art-label">GOOD IDEAS DESERVE A SPACE</div>
<div class="mini-comment first">
<span class="mini-avatar">A</span>
<div>
<b>Thanks for sharing this idea!</b>
<small>A thoughtful contribution.</small>
</div>
<span class="check">✓</span>
</div>
<div class="filter-mark">s<span>·</span>
<small>KEEP THE CONVERSATION GOING</small>
</div>
<div class="mini-comment second">
<span class="mini-avatar orange">?</span>
<div>
<b>A comment needs a closer look</b>
<small>
<span class="tiny-dot">
</span> Held for a verifier</small>
</div>
</div>
<div class="art-foot">Human judgment. A helpful filter.</div>
</div>
</section>
<section id="discussions">
<div class="section-head">
<div>
<p class="eyebrow">THE COMMUNITY BOARD</p>
<h2>Find something worth discussing.</h2>
</div>
<span class="muted">
<%=rows(request.getAttribute("forums")).size()%> forums</span>
</div>
<form class="searchbar" method="get" action="/app/forums">
<label class="search-label">
<span class="sr-only">Search forums</span>
<input name="q" maxlength="100" value="<%=e(request.getParameter("q"))%>" placeholder="Search conversations…">
</label>
<label>
<span class="sr-only">Forum type</span>
<select name="category">
<option value="">All topics</option>
<%for(var category:rows(request.getAttribute("categories"))){%>
<option value="<%=category.get("id")%>" <%=Objects.toString(category.get("id")).equals(request.getParameter("category"))?"selected":""%>>
<%=e(category.get("name"))%>
</option>
<%}%>
</select>
</label>
<button class="button dark" type="submit">Search</button>
<a class="quiet-link" href="/app/forums">Clear</a>
</form>
<div class="forum-grid">
<%for(var f:rows(request.getAttribute("forums"))){%>
<article class="forum-card">
<div class="card-visual tone-<%=((Number)f.get("id")).intValue()%3%>">
<%if(f.get("image_type")!=null){%>
<img src="/app/image?id=<%=f.get("id")%>" alt="Forum cover">
<%}else{%>
<span class="abstract-mark">
<%=((Number)f.get("id")).intValue()%3==0?"✳":((Number)f.get("id")).intValue()%3==1?"↗":"∞"%>
</span>
<span class="visual-caption">
<%=e(f.get("category"))%> / OPEN IDEAS</span>
<%}%>
</div>
<div class="card-content">
<span class="pill">
<%=e(f.get("category"))%>
</span>
<h3>
<a href="/app/forum?id=<%=f.get("id")%>">
<%=e(f.get("title"))%>
</a>
</h3>
<p>
<%=e(excerpt(f.get("body")))%>
</p>
<div class="card-foot">
<span>
<%=f.get("comment_count")%> comments</span>
<a href="/app/forum?id=<%=f.get("id")%>" aria-label="Read <%=e(f.get("title"))%>">Join discussion ↗</a>
</div>
</div>
</article>
<%}%>
</div>
<%if(rows(request.getAttribute("forums")).isEmpty()){%>
<div class="empty">
<h3>No conversations found.</h3>
<p>Try another search, or clear the filters.</p>
</div>
<%}%>
</section>
<div class="community-note">
<span class="note-icon">✳</span>
<div>
<h3>Everyone has something to add.</h3>
<p>Be curious. Be respectful. Help make this a community worth coming back to.</p>
</div>
<a href="/app/about">Our moderation approach ↗</a>
</div>
<%}else if(current.equals("login")||current.equals("register")||current.equals("verifier")){boolean registration=!current.equals("login");%>
<div class="auth-layout">
<section class="auth-intro">
<p class="eyebrow">A SPACE FOR YOUR NEXT IDEA</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">Less noise. More connection.<br>A community built around what you have to say.</p>
<div class="auth-decoration">“<span>Make every<br>conversation<br>
<em>count.</em>
</span>”</div>
</section>
<section class="panel auth-panel">
<p class="eyebrow">
<%=registration?(admin?"TRUSTED MODERATION":"YOUR SEAT AT THE TABLE"):"WELCOME BACK"%>
</p>
<h2>
<%=registration?(current.equals("verifier")?"Create verifier account":"Create your account"):"Sign in to your space"%>
</h2>
<p class="muted">
<%=registration?"Start with the details below.":"One sign-in for readers, verifiers, and admins."%>
</p>
<form method="post" action="/app/<%=e(current)%>" class="stack">
<input type="hidden" name="csrf" value="<%=csrf%>">
<%if(registration){%>
<label>Full name<input name="full_name" maxlength="100" autocomplete="name" required>
</label>
<label>Email address<input name="email" type="email" maxlength="254" autocomplete="email" required>
</label>
<%}%>
<label>Username<input name="username" minlength="3" maxlength="40" pattern="[a-zA-Z0-9_]{3,40}" autocomplete="username" required>
<%if(registration){%>
<small>3–40 letters, numbers, or underscores.</small>
<%}%>
</label>
<label>Password<input name="password" type="password" <%=registration?"minlength=\"10\"":""%> maxlength="128" autocomplete="<%=registration?"new-password":"current-password"%>" required>
<%if(registration){%>
<small>At least 10 characters.</small>
<%}%>
</label>
<%if(registration){%>
<label>Mobile number <span class="muted">(optional)</span>
<input name="phone" type="tel" pattern="[0-9]{10}" maxlength="10" autocomplete="tel">
<small>10 digits, without country code.</small>
</label>
<%}%>
<button class="button full" type="submit">
<%=registration?(current.equals("verifier")?"Create verifier":"Create account"):"Sign in"%> <span>↗</span>
</button>
</form>
<%if(!admin){%>
<p class="auth-switch">
<%=registration?"Already part of the community?":"New around here?"%> <a href="/app/<%=registration?"login":"register"%>">
<%=registration?"Sign in":"Create an account"%>
</a>
</p>
<%}%>
<%if(!registration){%>
<details class="demo-details">
<summary>Try a local demo account</summary>
<p>Admin: <code>admin / Admin@12345</code>
<br>Verifier: <code>verifier / Verifier@12345</code>
<br>Reader: <code>user / User@12345</code>
</p>
<small>Demo credentials are intended for local evaluation.</small>
</details>
<%}%>
</section>
</div>
<%}else if(current.equals("forum")){%>
<div class="breadcrumb">
<a href="/app/forums">← All conversations</a> / <%=e(forum.get("category"))%>
</div>
<article class="article panel">
<span class="pill">
<%=e(forum.get("category"))%>
</span>
<h1>
<%=e(forum.get("title"))%>
</h1>
<p class="muted">Started <%=e(date(forum.get("created_at")))%> · Community forum</p>
<%if(forum.get("image_type")!=null){%>
<img class="article-cover" src="/app/image?id=<%=forum.get("id")%>" alt="Forum cover">
<%}%>
<div class="prose preserve">
<%=e(forum.get("body"))%>
</div>
<%if(staff){%>
<div class="actions">
<%if(admin){%>
<a class="button secondary small" href="/app/edit-forum?id=<%=forum.get("id")%>">Edit forum</a>
<%}%>
<form method="post" action="/app/delete-forum" data-confirm="Remove this forum from public view? Its history will be retained.">
<input type="hidden" name="csrf" value="<%=csrf%>">
<input type="hidden" name="id" value="<%=forum.get("id")%>">
<button class="button danger small" type="submit">Remove forum</button>
</form>
</div>
<%}%>
</article>
<section class="discussion">
<div class="section-head">
<h2>The conversation <span class="count">
<%=rows(request.getAttribute("comments")).size()%>
</span>
</h2>
<span class="muted">A little respect goes a long way.</span>
</div>
<%for(var comment:rows(request.getAttribute("comments"))){%>
<article class="comment">
<span class="avatar">
<%=e(comment.get("username").toString().substring(0,1).toUpperCase(Locale.ROOT))%>
</span>
<div>
<p>
<strong>
<%=e(comment.get("username"))%>
</strong> <time>
<%=e(date(comment.get("created_at")))%>
</time>
</p>
<p class="preserve">
<%=e(comment.get("body"))%>
</p>
</div>
</article>
<%}%>
<%if(rows(request.getAttribute("comments")).isEmpty()){%>
<div class="empty">
<h3>Be the first to add an idea.</h3>
<p>A good question can start a great discussion.</p>
</div>
<%}%>
<%if(role.equals("USER")){%>
<form class="panel stack" method="post" action="/app/comment">
<input type="hidden" name="csrf" value="<%=csrf%>">
<input type="hidden" name="id" value="<%=forum.get("id")%>">
<h3>Add your perspective</h3>
<label>
<span class="sr-only">Your comment</span>
<textarea name="body" rows="4" maxlength="3000" required placeholder="Share an idea, ask a question, or keep the conversation going…">
</textarea>
</label>
<div class="form-foot">
<small>Flagged comments are held for review. Four flagged submissions block the account.<br>Your current warnings: <%=e(me.get("spam_strikes"))%> / 4.</small>
<button class="button" type="submit">Post comment ↗</button>
</div>
</form>
<%}else if(!signed){%>
<div class="panel join-prompt">
<h3>Your voice belongs here.</h3>
<p>
<a href="/app/login">Sign in</a> or <a href="/app/register">create an account</a> to join the discussion.</p>
</div>
<%}else{%>
<div class="notice">You are viewing as a moderator. Sign in with a reader account to post a comment.</div>
<%}%>
<%if(!rows(request.getAttribute("myComments")).isEmpty()){%>
<h3>Your private moderation history</h3>
<p class="muted">Only you and the moderation team can see these comments.</p>
<%for(var comment:rows(request.getAttribute("myComments"))){%>
<div class="panel private-comment">
<span class="status <%=e(comment.get("status"))%>">
<%=e(comment.get("status"))%>
</span>
<p class="preserve">
<%=e(comment.get("body"))%>
</p>
<small>Matched: <%=e(comment.get("matched_terms"))%>
<%if(!Objects.toString(comment.get("review_note"),"").isEmpty()){%> · Review note: <%=e(comment.get("review_note"))%>
<%}%>
</small>
</div>
<%}%>
<%}%>
</section>
<%}else if(current.equals("dashboard")){var stats=m(request.getAttribute("stats"));%>
<section class="page-heading">
<p class="eyebrow">WELCOME BACK, <%=e(me.get("username").toString().toUpperCase(Locale.ROOT))%>
</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">
<%=staff?"Keep the good ideas flowing. Here is what needs your attention.":"Your ideas, your conversations, and a place to call your own."%>
</p>
</section>
<div class="stats">
<%for(String key:List.of("forums","published","pending","readers")){if(!staff&&key.equals("pending"))continue;%>
<div class="stat">
<small>
<%=switch(key){case "forums"->"OPEN FORUMS";case "published"->"PUBLISHED COMMENTS";case "pending"->"AWAITING REVIEW";default->"ACTIVE READERS";}%>
</small>
<strong>
<%=e(stats.get(key))%>
</strong>
</div>
<%}%>
</div>
<div class="section-head">
<h2>
<%=staff?"Recent activity":"Your recent comments"%>
</h2>
<a href="<%=staff?"/app/audit":"/app/forums"%>">
<%=staff?"View activity log ↗":"Explore forums ↗"%>
</a>
</div>
<div class="panel">
<%if(staff){%>
<%for(var log:rows(request.getAttribute("recent"))){%>
<div class="log-row">
<span class="activity-dot">
</span>
<div>
<b>
<%=e(log.get("action").toString().replace('_',' ').toLowerCase(Locale.ROOT))%>
</b>
<p>
<%=e(log.get("detail"))%>
</p>
</div>
<small>
<%=e(log.get("username"))%> · <%=e(date(log.get("created_at")))%>
</small>
</div>
<%}%>
<%}else{%>
<p>Your warnings: <strong>
<%=e(me.get("spam_strikes"))%> / 4</strong>. A verifier can review a flagged comment and restore a blocked account.</p>
<%for(var comment:rows(request.getAttribute("recent"))){%>
<div class="log-row">
<div>
<a href="/app/forum?id=<%=comment.get("forum_id")%>">
<%=e(comment.get("title"))%>
</a>
<p>
<%=e(excerpt(comment.get("body")))%>
</p>
</div>
<span class="status <%=e(comment.get("status"))%>">
<%=e(comment.get("status"))%>
</span>
</div>
<%}%>
<%if(rows(request.getAttribute("recent")).isEmpty()){%>
<div class="empty">
<p>You have not posted a comment yet. Find a conversation and add your perspective.</p>
</div>
<%}%>
<%}%>
</div>
<%if(staff){%>
<div class="community-note">
<span class="note-icon">✓</span>
<div>
<h3>A second look makes a difference.</h3>
<p>Check flagged comments and decide what belongs in the conversation.</p>
</div>
<a class="button" href="/app/moderation">Open review queue ↗</a>
</div>
<%}%>
<%}else if(current.equals("moderation")){%>
<section class="page-heading">
<p class="eyebrow">THE HUMAN SIDE OF MODERATION</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">Review context, make a decision, and leave a useful note.</p>
</section>
<div class="tabs">
<%for(String status:List.of("PENDING","PUBLISHED","REJECTED","ALL")){%>
<a class="<%=status.equals(request.getAttribute("filterStatus"))?"selected":""%>" href="/app/moderation?status=<%=status%>">
<%=status.substring(0,1)+status.substring(1).toLowerCase(Locale.ROOT)%>
</a>
<%}%>
</div>
<%for(var comment:rows(request.getAttribute("comments"))){%>
<article class="panel review-card">
<div class="section-head">
<div>
<span class="status <%=e(comment.get("status"))%>">
<%=e(comment.get("status"))%>
</span>
<h3>
<a href="/app/forum?id=<%=comment.get("forum_id")%>">
<%=e(comment.get("title"))%>
</a>
</h3>
</div>
<small>#<%=comment.get("id")%> · <%=e(date(comment.get("created_at")))%>
</small>
</div>
<p class="byline">By <strong>
<%=e(comment.get("username"))%>
</strong>
</p>
<blockquote class="preserve">
<%=e(comment.get("body"))%>
</blockquote>
<p>
<small>Dictionary matches: <strong>
<%=e(Objects.toString(comment.get("matched_terms"),"").isEmpty()?"None":comment.get("matched_terms"))%>
</strong>
</small>
</p>
<%if(comment.get("status").equals("PENDING")){%>
<form class="review-form" method="post" action="/app/review">
<input type="hidden" name="csrf" value="<%=csrf%>">
<input type="hidden" name="id" value="<%=comment.get("id")%>">
<label>Review note <span class="muted">(optional)</span>
<input name="note" maxlength="500" placeholder="Add context for the reader…">
</label>
<div class="actions">
<button class="button small" name="decision" value="PUBLISHED" type="submit">✓ Approve & publish</button>
<button class="button danger small" name="decision" value="REJECTED" type="submit">Reject comment</button>
</div>
</form>
<%}else{%>
<small>Review note: <%=e(Objects.toString(comment.get("review_note"),"").isBlank()?"—":comment.get("review_note"))%>
</small>
<%}%>
</article>
<%}%>
<%if(rows(request.getAttribute("comments")).isEmpty()){%>
<div class="empty panel">
<div class="empty-symbol">✓</div>
<h2>All clear here.</h2>
<p>There are no comments in this view.</p>
</div>
<%}%>
<%}else if(current.equals("users")){%>
<section class="page-heading">
<p class="eyebrow">COMMUNITY PEOPLE</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">Manage reader access and see who is looking after the conversation.</p>
</section>
<div class="panel table-wrap">
<table>
<thead>
<tr>
<th>Person</th>
<th>Role</th>
<th>Warnings</th>
<th>Account</th>
<th>Actions</th>
</tr>
</thead>
<tbody>
<%for(var user:rows(request.getAttribute("users"))){boolean active=Boolean.TRUE.equals(user.get("active")),blocked=Boolean.TRUE.equals(user.get("blocked"));%>
<tr>
<td>
<b>
<%=e(user.get("full_name"))%>
</b>
<small>@<%=e(user.get("username"))%> · <%=e(user.get("email"))%>
</small>
<%if(!Objects.toString(user.get("phone"),"").isEmpty()){%>
<small>
<%=e(user.get("phone"))%>
</small>
<%}%>
</td>
<td>
<span class="pill">
<%=e(user.get("role"))%>
</span>
</td>
<td>
<%=e(user.get("spam_strikes"))%> / 4</td>
<td>
<span class="status <%=!active||blocked?"REJECTED":"PUBLISHED"%>">
<%=!active?"INACTIVE":blocked?"BLOCKED":"ACTIVE"%>
</span>
</td>
<td>
<%if(user.get("role").equals("USER")){%>
<form method="post" action="/app/account" class="actions" data-confirm="Update this reader’s account access?">
<input type="hidden" name="csrf" value="<%=csrf%>">
<input type="hidden" name="id" value="<%=user.get("id")%>">
<%if(!active||blocked){%>
<button class="button secondary small" name="action" value="unblock" type="submit">Restore & reset</button>
<%}else{%>
<button class="button secondary small" name="action" value="block" type="submit">Block</button>
<button class="text-button danger-text" name="action" value="deactivate" type="submit">Deactivate</button>
<%}%>
</form>
<%}else{%>
<span class="muted">Staff account</span>
<%}%>
</td>
</tr>
<%}%>
</tbody>
</table>
</div>
<p class="muted">Restoring an account reactivates it and resets warnings to zero. Deactivation retains history.</p>
<%}else if(current.equals("edit-forum")){%>
<section class="page-heading">
<p class="eyebrow">GIVE AN IDEA A HOME</p>
<h1>
<%=e(pageTitle)%>
</h1>
</section>
<div class="two-col">
<form class="panel stack" method="post" action="/app/edit-forum" enctype="multipart/form-data">
<input type="hidden" name="csrf" value="<%=csrf%>">
<%if(!forum.isEmpty()){%>
<input type="hidden" name="id" value="<%=forum.get("id")%>">
<%}%>
<label>Forum title<input name="title" maxlength="160" value="<%=e(forum.get("title"))%>" required>
</label>
<label>Forum type<select name="category_id" required>
<%for(var cat:rows(request.getAttribute("categories"))){%>
<option value="<%=cat.get("id")%>" <%=Objects.equals(cat.get("id"),forum.get("category_id"))?"selected":""%>>
<%=e(cat.get("name"))%>
</option>
<%}%>
</select>
</label>
<label>What is this conversation about?<textarea name="body" rows="9" maxlength="20000" required>
<%=e(forum.get("body"))%>
</textarea>
</label>
<label>Cover image <span class="muted">(optional)</span>
<input type="file" name="image" accept="image/png,image/jpeg">
<small>PNG or JPEG, up to 2 MB and 12 megapixels.</small>
</label>
<%if(forum.get("image_type")!=null){%>
<img class="image-preview" src="/app/image?id=<%=forum.get("id")%>" alt="Current forum cover">
<label class="check-label">
<input type="checkbox" name="remove_image" value="yes"> Remove current cover</label>
<%}%>
<div class="actions">
<button class="button" type="submit">Save forum ↗</button>
<a href="/app/forums">Cancel</a>
</div>
</form>
<aside>
<form class="panel stack" method="post" action="/app/category">
<input type="hidden" name="csrf" value="<%=csrf%>">
<p class="eyebrow">ROOM FOR A NEW TOPIC?</p>
<h3>Add a forum type</h3>
<label>Type name<input name="name" maxlength="80" required placeholder="e.g. Design">
</label>
<button class="button secondary" type="submit">Add type</button>
</form>
<div class="aside-note">
<h3>Start with a good question.</h3>
<p>Give your forum a clear title and enough context to help others join in.</p>
</div>
</aside>
</div>
<%}else if(current.equals("profile")){%>
<section class="page-heading">
<p class="eyebrow">YOUR COMMUNITY PROFILE</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">Signed in as @<%=e(me.get("username"))%> · <%=e(role)%>
</p>
</section>
<form class="panel stack profile-form" method="post" action="/app/profile">
<input type="hidden" name="csrf" value="<%=csrf%>">
<div class="field-grid">
<label>Full name<input name="full_name" value="<%=e(me.get("full_name"))%>" maxlength="100" required>
</label>
<label>Email address<input name="email" value="<%=e(me.get("email"))%>" type="email" maxlength="254" required>
</label>
<label>Mobile number<input name="phone" value="<%=e(me.get("phone"))%>" pattern="[0-9]{10}" maxlength="10" type="tel">
</label>
<label>Date of birth<input type="date" name="birth_date" value="<%=e(me.get("birth_date"))%>">
</label>
<label>Gender <span class="muted">(optional)</span>
<input name="gender" value="<%=e(me.get("gender"))%>" maxlength="30">
</label>
<label>City<input name="city" value="<%=e(me.get("city"))%>" maxlength="80">
</label>
<label>State<input name="state" value="<%=e(me.get("state"))%>" maxlength="80">
</label>
</div>
<label>Address<textarea name="address" maxlength="500" rows="3">
<%=e(me.get("address"))%>
</textarea>
</label>
<div>
<button class="button" type="submit">Save profile ↗</button>
</div>
</form>
<%}else if(current.equals("dictionary")){%>
<section class="page-heading">
<p class="eyebrow">A SIMPLE, TRANSPARENT FILTER</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">These terms send a comment to a verifier for a closer look.</p>
</section>
<div class="two-col">
<div class="panel">
<h2>Active dictionary</h2>
<div class="term-list">
<%for(Object term:(List<?>)request.getAttribute("terms")){%>
<span class="term">
<%=e(term)%>
</span>
<%}%>
</div>
<p class="muted">Edit <code>config/spam-words.csv</code> in the project folder. Changes apply to the next comment without restarting.</p>
<%if(admin){%>
<form class="stack dictionary-editor" method="post" action="/app/dictionary">
<input type="hidden" name="csrf" value="<%=csrf%>">
<label>Edit dictionary CSV<textarea name="csv" rows="8" maxlength="65536" required spellcheck="false"><%=e(request.getAttribute("dictionaryCsv"))%></textarea><small>Use one term per row, or comma-separated cells. A header named term is optional.</small></label>
<button class="button" type="submit">Save dictionary</button>
<small>The previous version is saved as config/spam-words.csv.bak. Invalid or empty dictionaries are rejected.</small>
</form>
<%}%>
</div>
<div class="panel">
<h3>How matching works</h3>
<p>Matching ignores case, normalizes Unicode, and compares whole words or phrases. Punctuation separates words.</p>
<p>For example, <code>SPAM!</code> matches <code>spam</code>, while <code>spammer</code> does not.</p>
<p>Each flagged comment adds one warning, regardless of how many terms match. The fourth flagged submission blocks the account.</p>
<p>Approval does not remove a warning. Use “Restore & reset” on the People page when appropriate.</p>
</div>
</div>
<%}else if(current.equals("audit")){%>
<section class="page-heading">
<p class="eyebrow">COMMUNITY ACTIVITY</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">The most recent 500 moderation and management actions.</p>
</section>
<div class="panel table-wrap">
<table>
<thead>
<tr>
<th>When</th>
<th>Who</th>
<th>Action</th>
<th>Details</th>
</tr>
</thead>
<tbody>
<%for(var log:rows(request.getAttribute("audit"))){%>
<tr>
<td>
<%=e(date(log.get("created_at")))%>
</td>
<td>
<%=e(log.get("username"))%>
</td>
<td>
<code>
<%=e(log.get("action"))%>
</code>
</td>
<td>
<%=e(log.get("detail"))%>
</td>
</tr>
<%}%>
</tbody>
</table>
</div>
<%}else if(current.equals("about")){%>
<section class="page-heading">
<p class="eyebrow">A LITTLE HELP, A HUMAN DECISION</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede">Spam Blocker helps a community keep conversations useful and welcoming.</p>
</section>
<div class="steps">
<div class="panel">
<span class="step-number">01</span>
<h2>Share an idea.</h2>
<p>Readers join a forum and post a comment. Empty comments are never accepted.</p>
</div>
<div class="panel">
<span class="step-number">02</span>
<h2>Let the filter check.</h2>
<p>A CSV dictionary catches whole words and phrases. A clean comment is published immediately. A flagged comment stays private.</p>
</div>
<div class="panel">
<span class="step-number">03</span>
<h2>Keep people in the loop.</h2>
<p>An admin or verifier reviews held comments and approves or rejects them. Four flagged submissions block the account; a moderator can restore it.</p>
</div>
</div>
<div class="panel prose">
<h2>What the filter can tell you</h2>
<p>A word list provides a simple signal. It cannot reliably judge intent, context, sarcasm, all advertising, or every kind of abuse. A flagged comment is a request for review.</p>
<h3>Who does what?</h3>
<p>
<b>Readers</b> browse forums, post comments, and edit their profiles. <b>Verifiers</b> review comments, remove forums, and manage reader access. <b>Admins</b> also create and edit forums, add forum types, and create verifiers.</p>
<p>This is a reconstructed local mini project based on the supplied report. It uses Java, JSP, JDBC, and an embedded H2 database. See the included documentation for the original report’s inconsistencies and reconstruction choices.</p>
</div>
<%}else if(current.equals("contact")){%>
<section class="page-heading">
<p class="eyebrow">COMMUNITY SUPPORT</p>
<h1>
<%=e(pageTitle)%>
</h1>
</section>
<div class="panel prose">
<h2>Talk to your community administrator.</h2>
<p>For a blocked account or a comment review, contact the person running this installation and share your username and the forum title.</p>
<p>Admins and verifiers can review the queue and restore reader accounts from the People page.</p>
<p>This local demo does not send emails or support messages.</p>
<a class="button" href="/app/forums">Back to the community ↗</a>
</div>
<%}else{%>
<div class="error-layout panel">
<p class="eyebrow">SOMETHING NEEDS A SECOND LOOK</p>
<h1>
<%=e(pageTitle)%>
</h1>
<p class="lede" role="alert">
<%=e(request.getAttribute("error"))%>
</p>
<div class="actions">
<button class="button" type="button" data-back>Go back</button>
<a href="/app/forums">Explore forums ↗</a>
<a href="/app/login">Sign in</a>
</div>
</div>
<%}%>
</main>
<footer>
<div class="container footer-inner">
<span>
<b>spamblocker.</b> A space for better conversations.</span>
<div>
<a href="/app/about">How it works</a>
<a href="/app/contact">Contact</a>
<span>LOCAL COMMUNITY DEMO</span>
</div>
</div>
</footer>
</body>
</html>
