package Apache2::AuthLDAP;

use strict;
use warnings;

use base qw(Apache2::Filter);
use Apache2::RequestRec ();
use Apache2::Access ();
use Apache2::RequestUtil ();
use Net::LDAP ();

use Apache2::Const -compile => qw(OK DECLINED HTTP_UNAUTHORIZED AUTH_REQUIRED M_MKACTIVITY M_CHECKOUT M_PROPPATCH M_MERGE M_DELETE);
use Net::LDAP qw(LDAP_SUCCESS LDAP_PROTOCOL_ERROR);

sub handler {
    my $r = shift;
    my ($res, $sent_pwd) = $r->get_basic_auth_pw;
    my $name = $r->user;

    my $basedn = $r->dir_config('BaseDN') || "";
    my $ldapserver = $r->dir_config('LDAPServer') || "localhost";
    my $ldapport = $r->dir_config('LDAPPort') || 389;
    my $uidattr = $r->dir_config('UIDAttr') || "uid";
    my $filter = $r->dir_config('UserSearchFilter') || "";
    my $groupBaseDN = $r->dir_config('GroupBaseDN') || $basedn;
    my $permAttr = $r->dir_config('PermAttr') || "cn";
    my $defaultPerm = $r->dir_config('DefaultPerm') || "r";
    my $permToPass = $r->dir_config('PermToPass') || "w";
    my $uriPrefix = $r->dir_config('UriPrefix') || "";
    my $debug = $r->dir_config('AuthnDebug') || 0;
    if (defined $name) {
        $filter = "(&($uidattr=$name)".$filter.")";
    }

    my $ldap = Net::LDAP->new($ldapserver);
    my $mesg = $ldap->bind;
    $mesg = $ldap->search(base   => $basedn,
                          scope  => "sub",
                          filter => $filter);
    if (!$mesg->is_error && $mesg->count == 1) {
        my $dn = $mesg->entry(0)->dn;
        print STDERR "1. User DN Entry:".$dn."\n   Request Uri:".$r->uri."\n";
        $mesg = $ldap->bind($dn, password => $sent_pwd);
        if (!$mesg->is_error) {
            $filter = "(|(&(objectClass=groupOfNames)(member=".$dn."))(&(objectClass=groupOfUniqueNames)(uniqueMember=".$dn.")))";
            $mesg = $ldap->search(base   => $basedn,
                                  scope  => "sub",
                                  filter => $filter);
            if ($mesg->is_error) {
                return Apache2::Const::HTTP_UNAUTHORIZED;
            } else {
                my @folders;
                my @perms;
                my $length = length($groupBaseDN) - 1;
                my $permLength = length($permAttr) + 1;
                foreach my $entry ($mesg->entries) {
                    my $entryDN = $entry->dn;
                    if ($debug > 0) {
                        print STDERR "2. DN Entry:".$entryDN."        ".rindex($entryDN, $groupBaseDN)."\n";
                    }
                    if (rindex($entryDN, $groupBaseDN) >= 0) {
                        push(@folders, substr($entryDN, 0, length($entryDN) - $length - 2));
                    }
                }
                for (my $i=0; $i<@folders; $i++) {
                    my $folder = $folders[$i];
                    if ($debug > 0) {
                        print STDERR "3. Folder:".$folder."\n";
                    }
                    if (substr($folder, 0, $permLength) =~ /$permAttr=/i) {
                        my $perm = substr($folder, $permLength, index($folder, ",") - $permLength);
                        $folder = substr($folder, index($folder, ",") + 1);
                        my $path = "";
                        do {
                            my $temp = substr($folder, rindex($folder, ",") + 1);
                            if (length($temp) > -1 && index($temp, "=") > -1) {
                                $path = $path."/".substr($temp, index($temp, "=") + 1);
                                if (rindex($folder, ",") > -1) {
                                    $folder = substr($folder, 0, rindex($folder, ","));
                                } else {
                                    $folder = "";
                                }
                            }
                            if ($debug > 0) {
                                print STDERR "4. Temp: ".$temp."  Path: ".$path."  Folder:".$folder."\n";
                            }
                        } while(length($folder) > 0);
                        if ($debug > 0) {
                            print STDERR "5. ".$path."  ".$perm."\n";
                        }
                        push(@perms, $uriPrefix.$path);
                        push(@perms, $perm);
                    }
                }
                my $uriPath = $r->uri;
                my @finalPerm = (0, "/", $defaultPerm);
                if ($uriPath =~ /\/!svn\/vcc\/default$/i || $uriPath =~ /\/!svn\/bln\/[a-z0-9-]+$/i || $uriPath =~ /\/!svn\/act\/[a-f0-9-]*$/i || $uriPath =~ /\/!svn\/wbl\/[a-f0-9-]*\/[0-9]*$/i) {
                    if ($debug > 0) {
                        print STDERR "5.1 ".length($uriPath)."      ".$uriPath."     r\n";
                    }
                    @finalPerm = (length($uriPath), $uriPath, "r");
                }
                $uriPath =~ s/!svn\/[a-z]+\/[a-z0-9-]+\///;
                for (my $i=0; $i<@perms;) {
                    my $path = $perms[$i];
                    my $perm = $perms[$i + 1];
                    $i++;
                    $i++;
                    if ($debug > 0) {
                        print STDERR "6. ".index($uriPath, $path)."\n";
                    }
                    if (index($uriPath, $path) == 0 && length($path) > $finalPerm[0]) {
                        if ($debug > 0) {
                            print STDERR "7.2 ".length($path)."      ".$path."     ".$perm."\n";
                        }
                        @finalPerm = (length($path), $path, $perm);
                    }
                }
                if ($debug > 0) {
                    print STDERR "8. ".$r->uri."    ".$uriPath."    ".$finalPerm[2]."    ".($finalPerm[2] =~ /^$permToPass$/i)."\n";
                }
                if ($finalPerm[2] =~ /^$permToPass$/i) {
                    $ldap->unbind;
                    my $limitExcept = $r->dir_config('LimitExcept') || "GET PROPFIND OPTIONS REPORT";
                    if ($debug > 0) {
                        print STDERR "9. Request method:".$r->method."     Is limited:".index($limitExcept, $r->method)."\n";
                    }
                    if (index($limitExcept, $r->method) == -1) {
                        $r->push_handlers(PerlAuthzHandler => \&authz);
                    }
                    return Apache2::Const::OK;
                } else {
                    $ldap->unbind;
                    $r->note_basic_auth_failure;
                    return Apache2::Const::AUTH_REQUIRED;
                }
            }
        }
    }
    $ldap->unbind;

    $r->note_basic_auth_failure;
    if (defined $name) {
        print STDERR "10. user $name: not authenticated: ".$r->uri."\n";
    }
    return Apache2::Const::AUTH_REQUIRED;
}

sub authz {
    my $r = shift;
    my $requires = $r->requires;
    my $name = $r->user;

    # MKACTIVITY, MERGE, CHECKOUT and PROPPATCH do not need authz 
    if ((($r->method_number == Apache2::Const::M_MKACTIVITY || $r->method_number == Apache2::Const::M_MERGE || $r->method_number == Apache2::Const::M_DELETE) && $r->uri =~ /^\/.*\/!svn\/act\/[a-f0-9-]*$/) || 
        ($r->method_number == Apache2::Const::M_CHECKOUT && $r->uri =~ /^\/.*\/!svn\/bln\/[0-9]*$/) ||
        ($r->method_number == Apache2::Const::M_PROPPATCH && $r->uri =~ /^\/.*\/!svn\/wbl\/[a-f0-9-]*\/[0-9]*$/)) {
        return Apache2::Const::OK;
    }
    my $basedn = $r->dir_config('BaseDN') || "";
    my $ldapserver = $r->dir_config('LDAPServer') || "localhost";
    my $ldapport = $r->dir_config('LDAPPort') || 389;
    my $uidattr = $r->dir_config('UIDAttr') || "uid";
    my $filter = $r->dir_config('UserSearchFilter') || "";
    my $groupBaseDN = $r->dir_config('GroupBaseDN') || $basedn;
    my $permAttr = $r->dir_config('PermAttr') || "cn";
    my $defaultPerm = $r->dir_config('DefaultPerm') || "r";
    my $permToPass = $r->dir_config('PermToPass') || "w";
    my $debug = $r->dir_config('AuthzDebug') || 0;
    if (defined $name) {
        $filter = "(&($uidattr=$name)".$filter.")";
    }

    return Apache2::Const::OK unless $requires;

    for my $req (@$requires) {
        my ($require, @rest) = split /\s+/, $req->{requirement};
        if ($debug > 0) {
            print STDERR "11. Authz Require: ".$require."  ".@rest."\n";
        }

        if ($require eq "group") {
            my $ldap = Net::LDAP->new($ldapserver);
            my $mesg = $ldap->bind;
            $mesg = $ldap->search(base   => $basedn,
                                  scope  => "sub",
                                  filter => $filter);
            if (!$mesg->is_error && $mesg->count == 1) {
                my $dn = $mesg->entry(0)->dn;
                if (!$mesg->is_error) {
                    $filter = "(|(&(objectClass=groupOfNames)(member=".$dn."))(&(objectClass=groupOfUniqueNames)(uniqueMember=".$dn.")))";
                    $mesg = $ldap->search(base   => $basedn,
                                  scope  => "sub",
                                  filter => $filter);
                    if ($mesg->is_error) {
                        return Apache2::Const::HTTP_UNAUTHORIZED;
                    } else {
                        my @folders;
                        my @perms;
                        my $length = length($groupBaseDN) - 1;
                        my $permLength = length($permAttr) + 1;
                        foreach my $entry ($mesg->entries) {
                            my $entryDN = $entry->dn;
                            if ($debug > 0) {
                                print STDERR "12. DN Entry:".$entryDN."        ".rindex($entryDN, $groupBaseDN)."\n";
                            }
                            if (rindex($entryDN, $groupBaseDN) >= 0) {
                                push(@folders, substr($entryDN, 0, length($entryDN) - $length - 2));
                            }
                        }
                        for (my $i=0; $i<@folders; $i++) {
                            my $folder = $folders[$i];
                            if ($debug > 0) {
                                print STDERR "13. Folder:".$folder."\n";
                            }
                            if (substr($folder, 0, $permLength) =~ /$permAttr=/i) {
                                my $perm = substr($folder, $permLength, index($folder, ",") - $permLength);
                                $folder = substr($folder, index($folder, ",") + 1);
                                my $path = "";
                                do {
                                    my $temp = substr($folder, rindex($folder, ",") + 1);
                                    if (length($temp) > -1 && index($temp, "=") > -1) {
                                        $path = $path."/".substr($temp, index($temp, "=") + 1);
                                        if (rindex($folder, ",") > -1) {
                                            $folder = substr($folder, 0, rindex($folder, ","));
                                        } else {
                                            $folder = "";
                                        }
                                    }
                                    if ($debug > 0) {
                                        print STDERR "14. Temp: ".$temp."  Path: ".$path."  Folder:".$folder."\n";
                                    }
                                } while(length($folder) > 0);
                                if ($debug > 0) {
                                    print STDERR "15. ".$path."  ".$perm."\n";
                                }
                                push(@perms, $path);
                                push(@perms, $perm);
                            }
                        }
                        my $uriPath = $r->uri;
                        $uriPath =~ s/!svn\/[a-z]+\/[a-z0-9-]+\///;
                        my @finalPerm = (0, "/", $defaultPerm);
                        for (my $i=0; $i<@perms;) {
                            my $path = $perms[$i];
                            my $perm = $perms[$i + 1];
                            $i++;
                            $i++;
                            if ($debug > 0) {
                                print STDERR "16. ".index($uriPath, $path)."\n";
                            }
                            if (index($uriPath, $path) == 0 && length($path) > $finalPerm[0]) {
                                if ($debug > 0) {
                                    print STDERR "17. ".length($path)."      ".$path."     ".$perm."\n";
                                }
                                @finalPerm = (length($path), $path, $perm);
                            }
                        }
                        if ($debug > 0) {
                            print STDERR "18. ".$r->uri."    ".$uriPath."    ".$finalPerm[2]."    ".(grep $finalPerm[2] eq $_, @rest)."\n";
                        }
                        if (grep $finalPerm[2] eq $_, @rest) {
                            if ($debug > 0) {
                                print STDERR "19. OK!"."\n";
                            }
                            $ldap->unbind;
                            return Apache2::Const::OK;
                        } else {
                            if ($debug > 0) {
                                print STDERR "20. AUTH_REQUIRED!"."\n";
                            }
                            $ldap->unbind;
                            $r->note_basic_auth_failure;
                            return Apache2::Const::AUTH_REQUIRED;
                        }
                    }
                }
            }
            $ldap->unbind;
        }
    }

    $r->note_basic_auth_failure;
    print STDERR "21. user $name: not authorized: ".$r->uri."\n";
    return Apache2::Const::AUTH_REQUIRED;
}

1;
__END__

=head1 NAME

Apache2::AuthLDAP - an LDAP auth Module depends on mod_perl and Net::LDAP

=head1 SYNOPSIS

Sample configuration of Directory: valid user can access.

PerlRequire /var/www/perl/startup.pl
Alias /perl /var/www/perl
<Directory /var/www/perl>
    PerlAuthenHandler Apache2::AuthLDAP
    AuthName "Perl Authentication"
    AuthType Basic

    # Any of the following variables can be set.  Defaults are listed
    # to the right.
    PerlSetVar BaseDN "o=langhua,c=cn"
    PerlSetVar LDAPServer localhost
    PerlSetVar LDAPPort 389
    PerlSetVar UIDAttr uid:caseExactmatch:
    PerlSetVar UserSearchFilter (|(objectClass=inetOrgPerson)(objectClass=organizationalPerson))
    PerlSetVar GroupBaseDN "ou=svn,ou=groups,ou=applications,o=langhua,c=cn"
    PerlSetVar PermToPass w

    require valid-user

    SetHandler perl-script
    PerlResponseHandler ModPerl::Registry
    PerlOptions +ParseHeaders
    Options +ExecCGI
</Directory>


Sample configuration of SVN access control: valid user can read and authorized user can write.

LoadModule dav_svn_module     modules/mod_dav_svn.so
<Location /opensource>
   DAV svn
   SVNPath /usr/local/svn/opensource/

   PerlAuthenHandler Apache2::AuthLDAP
   AuthName "Perl Authentication"
   AuthType Basic
   PerlSetVar BaseDN           "o=langhua,c=cn"
   PerlSetVar LDAPServer       localhost
   PerlSetVar LDAPPort         389
   PerlSetVar UIDAttr          uid:caseExactmatch:
   PerlSetVar UserSearchFilter (|(objectClass=inetOrgPerson)(objectClass=organizationalPerson))
   PerlSetVar PermAttr         cn
   PerlSetVar GroupBaseDN      "ou=svn,ou=groups,ou=applications,o=langhua,c=cn"
   PerlSetVar DefaultPerm      r
   PerlSetVar PermToPass       [rw]
   PerlSetVar AuthnDebug       0
   PerlSetVar AuthzDebug       1
   PerlSetVar LimitExcept      "GET PROPFIND OPTIONS REPORT"

   <LimitExcept GET PROPFIND OPTIONS REPORT>
       Require group w rw
   </LimitExcept>

   Require    valid-user         
</Location>

Sample configuration of ViewVC access control: valid user can access.

<Location /viewvc/boji/>
   PerlAuthenHandler Apache2::AuthLDAP
   AuthName "Langhua.biz ViewVC Authentication"
   AuthType Basic
   PerlSetVar BaseDN           "o=langhua,c=cn"
   PerlSetVar LDAPServer       localhost
   PerlSetVar LDAPPort         389
   PerlSetVar UIDAttr          uid:caseExactmatch:
   PerlSetVar UserSearchFilter (|(objectClass=inetOrgPerson)(objectClass=organizationalPerson))
   PerlSetVar PermAttr         cn
   PerlSetVar GroupBaseDN      "ou=svn,ou=groups,ou=applications,o=langhua,c=cn"
   PerlSetVar DefaultPerm      n
   PerlSetVar PermToPass       [rw]
   PerlSetVar AuthnDebug       0
   PerlSetVar AuthzDebug       0
   PerlSetVar UriPrefix        /viewvc

   Require valid-user
</Location>


Sample configuration of SVN access control: anybody can read and authorized user can write.

LoadModule dav_svn_module     modules/mod_dav_svn.so
<Location /opensource>
   DAV svn
   SVNPath /usr/local/svn/opensource/

   PerlAuthenHandler Apache2::AuthLDAP
   AuthName "Perl Authentication"
   AuthType Basic
   PerlSetVar BaseDN           "o=langhua,c=cn"
   PerlSetVar LDAPServer       localhost
   PerlSetVar LDAPPort         389
   PerlSetVar UIDAttr          uid:caseExactmatch:
   PerlSetVar UserSearchFilter (|(objectClass=inetOrgPerson)(objectClass=organizationalPerson))
   PerlSetVar PermAttr         cn
   PerlSetVar GroupBaseDN      "ou=svn,ou=groups,ou=applications,o=langhua,c=cn"
   PerlSetVar DefaultPerm      r
   PerlSetVar PermToPass       [rw]
   PerlSetVar AuthnDebug       0
   PerlSetVar AuthzDebug       1
   PerlSetVar LimitExcept      "GET PROPFIND OPTIONS REPORT"

   <LimitExcept GET PROPFIND OPTIONS REPORT>
       Require group w rw
   </LimitExcept>
</Location>

= head1 DESCRIPTION


=head1 AUTHOR

Shi Yusen <shiys@langhua.cn>

=head1 COPYRIGHT

This library is part of Langhua Opensource Foundation

Copyright (C) 2009  Beijing Langhua Ltd. (http://langhua.biz)

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3.0 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.

For the latest version about this module, please see the
project website: http://langhua.org/opensource/LIS/svn/
 
You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

=cut
