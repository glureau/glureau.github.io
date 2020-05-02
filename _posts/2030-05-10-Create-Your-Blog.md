---
title: "Create your static blog for free"
subtitle: "Use github user pages with Jekyll, your custom theme, and setup your domain name"
date: 2020-05-10
layout: post
tags: blog
author: Grégory Lureau
background: "/pictures/samantha-gades-BlIhVfXbi9s-unsplash.jpg"
---

How to create a blog like this one:

# Github pages

Github allows you to host and run a static website at no cost, without any advertizing.

# Jekyll

Custom theme not supported by github

Your own domain name

    www.glureau.com 3600 IN CNAME glureau.github.io

Check your configuration:
	
	$ dig www.glureau.com +nostats +nocomments +nocmd
	; <<>> DiG 9.10.6 <<>> www.glureau.com +nostats +nocomments +nocmd
	;; global options: +cmd
	;www.glureau.com.		IN	A
	**www.glureau.com.	3600	IN	CNAME	glureau.github.io.**
	glureau.github.io.	3600	IN	A	185.199.111.153
	glureau.github.io.	3600	IN	A	185.199.108.153
	glureau.github.io.	3600	IN	A	185.199.110.153
	glureau.github.io.	3600	IN	A	185.199.109.153
	
Resources:
https://help.github.com/en/github/working-with-github-pages/managing-a-custom-domain-for-your-github-pages-site
